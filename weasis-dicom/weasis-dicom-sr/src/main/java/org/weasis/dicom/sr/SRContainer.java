package org.weasis.dicom.sr;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.util.ForcedAcceptPrintService;
import org.weasis.core.ui.util.Toolbar;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomFieldsView;
import org.weasis.dicom.explorer.DicomModel;

public class SRContainer extends ImageViewerPlugin<DicomImageElement> implements PropertyChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(SRContainer.class);

    public static final List<SynchView> SYNCH_LIST = Collections.synchronizedList(new ArrayList<SynchView>());
    static {
        SYNCH_LIST.add(SynchView.NONE);
    }

    public static final List<GridBagLayoutModel> LAYOUT_LIST = Collections
        .synchronizedList(new ArrayList<GridBagLayoutModel>());

    public static final GridBagLayoutModel VIEWS_1x1 = new GridBagLayoutModel("1x1", //$NON-NLS-1$
        "1x1", 1, 1, SRView.class.getName(), new ImageIcon(ImageViewerPlugin.class //$NON-NLS-1$ 
            .getResource("/icon/22x22/layout1x1.png"))); //$NON-NLS-1$
    static {
        LAYOUT_LIST.add(VIEWS_1x1);
    }

    // Static tools shared by all the View2dContainer instances, tools are registered when a container is selected
    // Do not initialize tools in a static block (order initialization issue with eventManager), use instead a lazy
    // initialization with a method.
    public static final List<Toolbar> TOOLBARS = Collections.synchronizedList(new ArrayList<Toolbar>(1));
    public static final List<DockableTool> TOOLS = Collections.synchronizedList(new ArrayList<DockableTool>(1));
    private static volatile boolean INI_COMPONENTS = false;
    static final ImageViewerEventManager<DicomImageElement> SR_EVENT_MANAGER =
        new ImageViewerEventManager<DicomImageElement>() {

            @Override
            public boolean updateComponentsListener(DefaultView2d<DicomImageElement> defaultView2d) {
                // Do nothing
                return true;
            }

            @Override
            public void resetDisplay() {
                // Do nothing
            }

            @Override
            public void setSelectedView2dContainer(ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
                this.selectedView2dContainer = selectedView2dContainer;
            }

        };

    protected SRView srview;

    public SRContainer() {
        this(VIEWS_1x1, null);
    }

    public SRContainer(GridBagLayoutModel layoutModel, String uid) {
        super(SR_EVENT_MANAGER, layoutModel, uid, SRFactory.NAME, SRFactory.ICON, null);
        setSynchView(SynchView.NONE);
        if (!INI_COMPONENTS) {
            INI_COMPONENTS = true;
            // Add standard toolbars
            final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
            String bundleName = context.getBundle().getSymbolicName();
            String componentName = InsertableUtil.getCName(this.getClass());
            String key = "enable"; //$NON-NLS-1$

            if (InsertableUtil.getBooleanProperty(BundleTools.SYSTEM_PREFERENCES, bundleName, componentName,
                InsertableUtil.getCName(SrToolBar.class), key, true)) {
                TOOLBARS.add(new SrToolBar<DicomImageElement>(10));
            }
        }
    }

    @Override
    public void setSelectedImagePaneFromFocus(DefaultView2d<DicomImageElement> defaultView2d) {
        setSelectedImagePane(defaultView2d);
    }

    @Override
    public JMenu fillSelectedPluginMenu(JMenu menuRoot) {
        if (menuRoot != null) {
            menuRoot.removeAll();

            List<Action> actions = getPrintActions();
            if (actions != null) {
                JMenu printMenu = new JMenu(Messages.getString("SRContainer.print")); //$NON-NLS-1$
                for (Action action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    printMenu.add(item);
                }
                menuRoot.add(printMenu);
            }
        }
        return menuRoot;
    }

    @Override
    public List<DockableTool> getToolPanel() {
        return TOOLS;
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            eventManager.setSelectedView2dContainer(this);

            // Send event to select the related patient in Dicom Explorer.
            DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomView != null && dicomView.getDataExplorerModel() instanceof DicomModel) {
                dicomView.getDataExplorerModel().firePropertyChange(
                    new ObservableEvent(ObservableEvent.BasicAction.Select, this, null, getGroupID()));
            }

        } else {
            eventManager.setSelectedView2dContainer(null);
        }
    }

    @Override
    public void close() {
        super.close();
        SRFactory.closeSeriesViewer(this);

        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (srview != null) {
                    srview.dispose();
                }
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            // if (ObservableEvent.BasicAction.Update.equals(action)) {
            // if (newVal instanceof Series) {
            // Series series = (Series) newVal;
            // if (srview != null && srview.getSeries() != series) {
            // srview.setSeries(series);
            // }
            // }
            // }
            if (ObservableEvent.BasicAction.Remove.equals(action)) {
                if (newVal instanceof DicomSeries) {
                    if (srview != null && srview.getSeries() == newVal) {
                        close();
                    }
                } else if (newVal instanceof MediaSeriesGroup) {
                    MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                    // Patient Group
                    if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                        if (group.equals(getGroupID())) {
                            // Close the content of the plug-in
                            close();
                        }
                    }
                    // Study Group
                    else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                        if (event.getSource() instanceof DicomModel) {
                            DicomModel model = (DicomModel) event.getSource();
                            for (MediaSeriesGroup s : model.getChildren(group)) {
                                if (srview != null && srview.getSeries() == s) {
                                    close();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getViewTypeNumber(GridBagLayoutModel layout, Class defaultClass) {
        return 0;
    }

    @Override
    public boolean isViewType(Class defaultClass, String type) {
        if (defaultClass != null) {
            try {
                Class clazz = Class.forName(type);
                return defaultClass.isAssignableFrom(clazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
        return null;
    }

    @Override
    public JComponent createUIcomponent(String clazz) {
        try {
            // FIXME use classloader.loadClass or injection
            Class cl = Class.forName(clazz);
            JComponent component = (JComponent) cl.newInstance();
            if (component instanceof SeriesViewerListener) {
                eventManager.addSeriesViewerListener((SeriesViewerListener) component);
            }
            if (component instanceof SRView) {
                srview = (SRView) component;
            }
            return component;
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return null;
    }

    @Override
    public synchronized WtoolBar getStatusBar() {
        return null;
    }

    @Override
    public synchronized List<Toolbar> getToolBar() {
        return TOOLBARS;
    }

    @Override
    public List<Action> getExportActions() {
        return null;
    }

    @Override
    public List<Action> getPrintActions() {
        ArrayList<Action> actions = new ArrayList<Action>(1);
        final String title = Messages.getString("SRContainer.print_layout"); //$NON-NLS-1$
        AbstractAction printStd =
            new AbstractAction(title, new ImageIcon(ImageViewerPlugin.class.getResource("/icon/16x16/printer.png"))) { //$NON-NLS-1$

                @Override
                public void actionPerformed(ActionEvent action) {
                    printCurrentView();
                }
            };
        actions.add(printStd);

        return actions;
    }

    void displayHeader() {
        if (srview != null) {
            JFrame frame = new JFrame(org.weasis.dicom.explorer.Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
            frame.setSize(500, 630);
            DicomFieldsView view = new DicomFieldsView();
            view.changingViewContentEvent(new SeriesViewerEvent(this, srview.getSeries(), DicomModel
                .getFirstSpecialElement(srview.getSeries(), DicomSpecialElement.class), EVENT.SELECT));
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(view);
            frame.getContentPane().add(panel);
            frame.setVisible(true);
        }
    }

    void printCurrentView() {
        if (srview != null) {
            PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
            PrinterJob pj = PrinterJob.getPrinterJob();

            // Get page format from the printer
            if (pj.printDialog(aset)) {
                PageFormat pageFormat = pj.defaultPage();
                // Force to print in black and white
                EditorPanePrinter pnlPreview =
                    new EditorPanePrinter(srview.getHtmlPanel(), pageFormat, new Insets(18, 18, 18, 18));
                pj.setPageable(pnlPreview);
                try {
                    pj.print();
                } catch (PrinterException e) {
                    // check for the annoying 'Printer is not accepting job' error.
                    if (e.getMessage().indexOf("accepting job") != -1) { //$NON-NLS-1$
                        // recommend prompting the user at this point if they want to force it
                        // so they'll know there may be a problem.
                        int response =
                            JOptionPane
                                .showConfirmDialog(
                                    null,
                                    org.weasis.core.ui.Messages.getString("ImagePrint.issue_desc"), //$NON-NLS-1$
                                    org.weasis.core.ui.Messages.getString("ImagePrint.status"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$

                        if (response == 0) {
                            try {
                                // try printing again but ignore the not-accepting-jobs attribute
                                ForcedAcceptPrintService.setupPrintJob(pj); // add secret ingredient
                                pj.print(aset);
                                LOGGER.info("Bypass Printer is not accepting job"); //$NON-NLS-1$
                            } catch (PrinterException ex) {
                                AuditLog.logError(LOGGER, e, "Printer exception"); //$NON-NLS-1$
                            }
                        }
                    } else {
                        AuditLog.logError(LOGGER, e, "Print exception"); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    @Override
    public void addSeries(MediaSeries<DicomImageElement> sequence) {
        if (srview != null && sequence instanceof Series && srview.getSeries() != sequence) {
            srview.setSeries((Series<DicomImageElement>) sequence);
        }
    }

    @Override
    public void addSeriesList(List<MediaSeries<DicomImageElement>> seriesList, boolean removeOldSeries) {
        if (seriesList != null && seriesList.size() > 0) {
            addSeries(seriesList.get(0));
        }
    }

    @Override
    public void selectLayoutPositionForAddingSeries(List<MediaSeries<DicomImageElement>> seriesList) {
        // Do it in addSeries()
    }

    @Override
    public List<SynchView> getSynchList() {
        return SYNCH_LIST;
    }

    @Override
    public List<GridBagLayoutModel> getLayoutList() {
        return LAYOUT_LIST;
    }

}
