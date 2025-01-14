package org.weasis.dicom.viewer2d.mip;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.task.TaskInterruptionException;
import org.weasis.core.api.gui.task.TaskMonitor;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2d;
import org.weasis.dicom.viewer2d.View2dFactory;

public class MipView extends View2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(MipView.class);

    public static final ImageIcon MIP_ICON_SETTING = new ImageIcon(
        MipView.class.getResource("/icon/22x22/mip-setting.png")); //$NON-NLS-1$
    public static final ActionW MIP = new ActionW(Messages.getString("MipView.mip"), "mip", 0, 0, null); //$NON-NLS-1$ //$NON-NLS-2$
    public static final ActionW MIP_THICKNESS = new ActionW("Image Extension", "mip_thick", 0, 0, null); //$NON-NLS-2$

    public enum Type {
        MIN, MEAN, MAX;
    };

    private volatile MipProcess process;

    public MipView(ImageViewerEventManager<DicomImageElement> eventManager) {
        super(eventManager);
    }

    @Override
    protected void initActionWState() {
        super.initActionWState();
        actionsInView.put(DefaultView2d.zoomTypeCmd, ZoomType.BEST_FIT);
        actionsInView.put(MIP_THICKNESS.cmd(), 2);
        actionsInView.put(MipView.MIP.cmd(), MipView.Type.MAX);
        actionsInView.put("no.ko", true); //$NON-NLS-1$

        // Propagate the preset
        OpManager disOp = getDisplayOpManager();
        disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), false);
        // disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);
    }

    public void initMIPSeries(DefaultView2d selView) {
        if (selView != null) {
            actionsInView.put(ActionW.SORTSTACK.cmd(), selView.getActionValue(ActionW.SORTSTACK.cmd()));
            actionsInView.put(ActionW.INVERSESTACK.cmd(), selView.getActionValue(ActionW.INVERSESTACK.cmd()));
            actionsInView.put(ActionW.FILTERED_SERIES.cmd(), selView.getActionValue(ActionW.FILTERED_SERIES.cmd()));
            MediaSeries s = selView.getSeries();
            setSeries(s, null);
        }
    }

    @Override
    protected void setImage(DicomImageElement img) {
        // Avoid to listen synch events
    }

    public boolean IsProcessRunning() {
        return process != null;
    }

    protected void stopCurrentProcess() {
        final MipProcess t = process;
        if (t != null) {
            process = null;
            // Close won't stop the process immediately
            t.taskMonitor.close();
            t.interrupt();
        }
    }

    public void exitMipMode(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
        // reset current process
        this.setActionsInView(MipView.MIP.cmd(), null);
        this.setActionsInView(MipView.MIP_THICKNESS.cmd(), null);

        setMip(null);

        ImageViewerPlugin<DicomImageElement> container = this.getEventManager().getSelectedView2dContainer();
        container.setSelectedAndGetFocus();
        View2d newView2d = new View2d(this.getEventManager());
        newView2d.registerDefaultListeners();
        newView2d.setSeries(series, selectedDicom);
        container.replaceView(this, newView2d);
    }

    public static void buildMip(JDialog dialog, final MipView view, final boolean fullSeries) {
        if (view == null) {
            return;
        }
        view.stopCurrentProcess();

        final Type mipType = (Type) view.getActionValue(MipView.MIP.cmd());
        final Integer extend = (Integer) view.getActionValue(MIP_THICKNESS.cmd());
        final MediaSeries<DicomImageElement> ser = view.series;
        if (ser == null || extend == null || mipType == null) {
            return;
        }

        view.process =
            new MipProcess(
                Messages.getString("MipView.build"), new TaskMonitor(dialog == null ? view : dialog, "Monitoring Progress", "Initializing...", 0, 2 * extend + 1)) { //$NON-NLS-1$
                @Override
                public void run() {
                    final List<DicomImageElement> dicoms = new ArrayList<DicomImageElement>();
                    try {
                        taskMonitor.setMillisToPopup(1250);
                        SeriesBuilder.applyMipParameters(taskMonitor, view, ser, dicoms, mipType, extend, fullSeries);
                    } catch (TaskInterruptionException e) {
                        dicoms.clear();
                        LOGGER.info(e.getMessage());
                    } catch (Throwable t) {
                        dicoms.clear();
                        AuditLog.logError(LOGGER, t, "Mip renderding error");
                    } finally {
                        // Following actions need to be executed in EDT thread
                        GuiExecutor.instance().execute(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    if (dicoms.size() == 1) {
                                        view.setMip(dicoms.get(0));
                                    } else if (dicoms.size() > 1) {
                                        DicomImageElement dcm = dicoms.get(0);
                                        Series s = new DicomSeries((String) dcm.getTagValue(TagW.SeriesInstanceUID));
                                        s.addAll(dicoms);
                                        ((DcmMediaReader) dcm.getMediaReader()).writeMetaData(s);
                                        DataExplorerModel model =
                                            (DataExplorerModel) ser.getTagValue(TagW.ExplorerModel);
                                        if (model instanceof DicomModel) {
                                            DicomModel dicomModel = (DicomModel) model;
                                            MediaSeriesGroup study = dicomModel.getParent(ser, DicomModel.study);
                                            if (study != null) {
                                                s.setTag(TagW.ExplorerModel, dicomModel);
                                                dicomModel.addHierarchyNode(study, s);
                                                dicomModel.firePropertyChange(new ObservableEvent(
                                                    ObservableEvent.BasicAction.Add, dicomModel, null, s));
                                            }

                                            View2dFactory factory = new View2dFactory();
                                            ViewerPluginBuilder.openSequenceInPlugin(factory, s, model, false, false);
                                        }
                                    }
                                } finally {
                                    taskMonitor.close();
                                }
                            }
                        });
                    }
                }
            };
        view.process.start();

    }

    protected void setMip(DicomImageElement dicom) {
        DicomImageElement oldImage = getImage();
        if (dicom != null) {
            // Trick: call super to change the image as "this" method is empty
            super.setImage(dicom);
        }

        if (oldImage == null) {
            eventManager.updateComponentsListener(MipView.this);
        } else {
            // Close stream
            oldImage.dispose();
            // Delete file in cache
            File file = oldImage.getFile();
            if (file != null) {
                file.delete();
            }
        }
    }

    static class MipProcess extends Thread {
        final TaskMonitor taskMonitor;

        public MipProcess(String name, TaskMonitor taskMonitor) {
            super(name);
            this.taskMonitor = taskMonitor;
        }

    }

}
