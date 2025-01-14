/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.DefaultMimeAppFactory;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.ArrayListComboBoxModel;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.ui.util.WrapLayout;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadSeries;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public class DicomExplorer extends PluginTool implements DataExplorerView, SeriesViewerListener {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DicomExplorer.class);

    public static final String NAME = Messages.getString("DicomExplorer.title"); //$NON-NLS-1$
    public static final String PREFERENCE_NODE = "dicom.explorer"; //$NON-NLS-1$
    public static final String BUTTON_NAME = Messages.getString("DicomExplorer.btn_title"); //$NON-NLS-1$
    public static final String DESCRIPTION = Messages.getString("DicomExplorer.desc"); //$NON-NLS-1$
    // private static final JMenuItem openDicomMenu = new JMenuItem(OpenMediaAction.getInstance());
    public static final String ALL_PATIENTS = Messages.getString("DicomExplorer.sel_all_pat"); //$NON-NLS-1$
    public static final String ALL_STUDIES = Messages.getString("DicomExplorer.sel_all_st"); //$NON-NLS-1$
    public static final Icon PATIENT_ICON = new ImageIcon(DicomExplorer.class.getResource("/icon/16x16/patient.png")); //$NON-NLS-1$

    private JPanel panel = null;
    private JPanel panel_4 = null;
    private PatientPane selectedPatient = null;
    final CircularProgressBar globalProgress = new CircularProgressBar(0, 100);
    private final List<PatientPane> patientPaneList = new ArrayList<PatientPane>();
    private final HashMap<MediaSeriesGroup, List<StudyPane>> patient2study =
        new HashMap<MediaSeriesGroup, List<StudyPane>>();
    private final HashMap<MediaSeriesGroup, List<SeriesPane>> study2series =
        new HashMap<MediaSeriesGroup, List<SeriesPane>>();
    private final JScrollPane thumnailView = new JScrollPane();
    private final SeriesSelectionModel selectionList;
    private final ArrayList<ExplorerTask> tasks = new ArrayList<ExplorerTask>();

    private final DicomModel model;

    private final ArrayListComboBoxModel modelPatient = new ArrayListComboBoxModel() {

        private static final long serialVersionUID = 1826724555734323483L;

        @Override
        public void addElement(Object anObject) {
            int index = binarySearch(anObject, DicomModel.PATIENT_COMPARATOR);
            if (index < 0) {
                super.insertElementAt(anObject, -(index + 1));
            } else {
                super.insertElementAt(anObject, index);
            }
        }
    };
    private final ArrayListComboBoxModel modelStudy = new ArrayListComboBoxModel() {

        private static final long serialVersionUID = 2272386715266884376L;

        @Override
        public void addElement(Object anObject) {
            int index = binarySearch(anObject, DicomModel.STUDY_COMPARATOR);
            if (index < 0) {
                super.insertElementAt(anObject, -(index + 1));
            } else {
                super.insertElementAt(anObject, index);
            }
        }
    };
    private final JComboBox patientCombobox = new JComboBox(modelPatient);
    private final JComboBox studyCombobox = new JComboBox(modelStudy);
    protected final PatientContainerPane patientContainer = new PatientContainerPane();
    private final transient ItemListener patientChangeListener = new ItemListener() {

        @Override
        public void itemStateChanged(final ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Object item = modelPatient.getSelectedItem();
                if (item instanceof MediaSeriesGroupNode) {
                    MediaSeriesGroupNode patient = (MediaSeriesGroupNode) item;
                    selectPatient(patient);
                } else if (item != null) {
                    selectPatient(null);
                }
                patientContainer.revalidate();
                patientContainer.repaint();
            }
        }
    };
    private final transient ItemListener studyItemListener = new ItemListener() {

        @Override
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectStudy();
            }

        }
    };
    private final JSlider slider = new JSlider(Thumbnail.MIN_SIZE, Thumbnail.MAX_SIZE, Thumbnail.DEFAULT_SIZE);
    private JPanel panel_1 = null;
    private final JPanel panel_2 = new JPanel();
    private final JToggleButton btnMoreOptions = new JToggleButton(Messages.getString("DicomExplorer.more_opt")); //$NON-NLS-1$
    private final JPanel panel_3 = new JPanel();

    private final AbstractAction importAction = new AbstractAction(BUTTON_NAME) {

        @Override
        public void actionPerformed(ActionEvent e) {
            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(DicomExplorer.this);
            DicomImport dialog = new DicomImport(SwingUtilities.getWindowAncestor(DicomExplorer.this), model);
            dialog.showPage(BUTTON_NAME);
            ColorLayerUI.showCenterScreen(dialog, layer);
        }
    };
    private final AbstractAction exportAction = new AbstractAction(BUTTON_NAME) {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.export.dicom", true)) { //$NON-NLS-1$
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(DicomExplorer.this);
                DicomExport dialog = new DicomExport(SwingUtilities.getWindowAncestor(DicomExplorer.this), model);
                dialog.showPage(BUTTON_NAME);
                ColorLayerUI.showCenterScreen(dialog, layer);
            } else {
                JOptionPane.showMessageDialog((Component) e.getSource(),
                    Messages.getString("DicomExplorer.export_perm")); //$NON-NLS-1$
            }
        }
    };

    private final JButton btnExport = new JButton(exportAction);
    private final JButton btnImport = new JButton(importAction);
    private final JButton koOpen = new JButton(Messages.getString("DicomExplorer.open_ko"), new ImageIcon( //$NON-NLS-1$
        DicomExplorer.class.getResource("/icon/16x16/key-images.png"))); //$NON-NLS-1$

    private final JLabel globalLoadingLabel = new JLabel();
    private final JButton globalResumeButton = new JButton(new Icon() {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.GREEN);
            x += 3;
            y += 3;
            int[] xPoints = { x, x + 14, x };
            int[] yPoints = { y, y + 7, y + 14 };
            g2d.fillPolygon(xPoints, yPoints, xPoints.length);
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    });

    private final JButton globalStopButton = new JButton(new Icon() {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.RED);
            x += 3;
            y += 3;
            g2d.fillRect(x, y, 14, 14);
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    });

    public DicomExplorer() {
        this(null);
    }

    public DicomExplorer(DicomModel model) {
        super(BUTTON_NAME, NAME, POSITION.WEST, ExtendedMode.NORMALIZED, PluginTool.Type.EXPLORER, 20);
        setLayout(new BorderLayout());
        setDockableWidth(180);
        dockable.setMaximizable(true);
        this.model = model == null ? new DicomModel() : model;
        this.selectionList = new SeriesSelectionModel(patientContainer);
        thumnailView.getVerticalScrollBar().setUnitIncrement(16);
        thumnailView.setViewportView(patientContainer);
        changeToolWindowAnchor(getDockable().getBaseLocation());

    }

    public SeriesSelectionModel getSelectionList() {
        return selectionList;
    }

    private void removePatientPane(MediaSeriesGroup patient) {
        for (int i = 0; i < patientPaneList.size(); i++) {
            PatientPane p = patientPaneList.get(i);
            if (p.isPatient(patient)) {
                patientPaneList.remove(i);
                List<StudyPane> studies = patient2study.remove(patient);
                if (studies != null) {
                    for (StudyPane studyPane : studies) {
                        study2series.remove(studyPane.dicomStudy);
                    }
                }
                patientContainer.remove(p);
                modelPatient.removeElement(patient);
                if (modelPatient.getSize() == 0) {
                    modelStudy.removeAllElements();
                    modelStudy.insertElementAt(ALL_STUDIES, 0);
                    modelStudy.setSelectedItem(ALL_STUDIES);
                }
                return;
            }
        }
    }

    private void removeStudy(MediaSeriesGroup study) {
        MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
        List<StudyPane> studies = patient2study.get(patient);
        if (studies != null) {
            for (int i = 0; i < studies.size(); i++) {
                StudyPane st = studies.get(i);
                if (st.isStudy(study)) {
                    studies.remove(i);
                    if (studies.size() == 0) {
                        patient2study.remove(patient);
                        // throw a new event for removing the patient
                        model.removePatient(patient);
                        break;
                    }
                    study2series.remove(study);
                    PatientPane patientPane = getPatientPane(patient);
                    if (patientPane != null && patientPane.isStudyVisible(study)) {
                        patientPane.remove(st);
                        modelStudy.removeElement(study);
                        patientPane.revalidate();
                        patientPane.repaint();
                    }
                    return;
                }
            }
        }
        study2series.remove(study);
    }

    private void removeSeries(MediaSeriesGroup series) {
        MediaSeriesGroup study = model.getParent(series, DicomModel.study);
        List<SeriesPane> seriesList = study2series.get(study);
        if (seriesList != null) {
            for (int j = 0; j < seriesList.size(); j++) {
                SeriesPane se = seriesList.get(j);
                if (se.isSeries(series)) {
                    seriesList.remove(j);
                    if (seriesList.size() == 0) {
                        study2series.remove(study);
                        // throw a new event for removing the patient
                        model.removeStudy(study);
                        break;
                    }
                    se.removeAll();

                    StudyPane studyPane = getStudyPane(study);
                    if (studyPane != null && studyPane.isSeriesVisible(series)) {
                        studyPane.remove(se);
                        studyPane.revalidate();
                        studyPane.repaint();
                    }
                    break;
                }
            }
        }
    }

    private PatientPane createPatientPane(MediaSeriesGroup patient) {
        PatientPane pat = getPatientPane(patient);
        if (pat == null) {
            pat = new PatientPane(patient);
            patientPaneList.add(pat);
        }
        return pat;
    }

    private PatientPane getPatientPane(MediaSeriesGroup patient) {
        for (PatientPane p : patientPaneList) {
            if (p.isPatient(patient)) {
                return p;
            }
        }
        return null;
    }

    private StudyPane getStudyPane(MediaSeriesGroup study) {
        List<StudyPane> studies = patient2study.get(model.getParent(study, DicomModel.patient));
        if (studies != null) {
            for (int i = 0; i < studies.size(); i++) {
                StudyPane st = studies.get(i);
                if (st.isStudy(study)) {
                    return st;
                }
            }
        }
        return null;
    }

    private StudyPane createStudyPaneInstance(MediaSeriesGroup study, int[] position) {
        StudyPane studyPane = getStudyPane(study);
        if (studyPane == null) {
            studyPane = new StudyPane(study);
            List<StudyPane> studies = patient2study.get(model.getParent(study, DicomModel.patient));
            if (studies != null) {
                int index = Collections.binarySearch(studies, studyPane, DicomModel.STUDY_COMPARATOR);
                if (index < 0) {
                    index = -(index + 1);
                } else {
                    index = studies.size();
                }
                if (position != null) {
                    position[0] = index;
                }
                studies.add(index, studyPane);
            }
        } else if (position != null) {
            position[0] = -1;
        }
        return studyPane;
    }

    private void updateThumbnailSize() {
        int thumbnailSize = slider.getValue();
        for (PatientPane p : patientContainer.getPatientPaneList()) {
            for (StudyPane studyPane : p.getStudyPaneList()) {
                for (SeriesPane series : studyPane.getSeriesPaneList()) {
                    series.updateSize(thumbnailSize);
                }
                studyPane.doLayout();
            }
        }
        patientContainer.revalidate();
        patientContainer.repaint();

    }

    private SeriesPane getSeriesPane(MediaSeriesGroup series) {
        List<SeriesPane> seriesList = study2series.get(model.getParent(series, DicomModel.study));
        if (seriesList != null) {
            for (int j = 0; j < seriesList.size(); j++) {
                SeriesPane se = seriesList.get(j);
                if (se.isSeries(series)) {
                    return se;
                }
            }
        }
        return null;
    }

    private synchronized SeriesPane createSeriesPaneInstance(MediaSeriesGroup series, int[] position) {
        SeriesPane seriesPane = getSeriesPane(series);
        if (seriesPane == null) {
            seriesPane = new SeriesPane(series);
            List<SeriesPane> seriesList = study2series.get(model.getParent(series, DicomModel.study));
            if (seriesList != null) {
                int index = Collections.binarySearch(seriesList, seriesPane, DicomModel.SERIES_COMPARATOR);
                if (index < 0) {
                    index = -(index + 1);
                } else {
                    index = seriesList.size();
                }
                if (position != null) {
                    position[0] = index;
                }
                seriesList.add(index, seriesPane);
            }
        } else if (position != null) {
            position[0] = -1;
        }
        return seriesPane;
    }

    private boolean isSelectedPatient(MediaSeriesGroup patient) {
        if (selectedPatient != null && selectedPatient.patient == patient) {
            return true;
        }
        return false;
    }

    class TitleBorder extends TitledBorder {

        public TitleBorder(String title) {
            super(title);
            setFont(FontTools.getFont10());
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            // Measure the title length
            FontRenderContext frc = ((Graphics2D) g).getFontRenderContext();
            Rectangle bound = getTitleFont().getStringBounds(title, frc).getBounds();
            int panelLength = width - 15;
            if (bound.width > panelLength) {
                int length = (title.length() * panelLength) / bound.width;
                if (length > 2) {
                    title = title.substring(0, length - 2) + "..."; //$NON-NLS-1$
                }
            }
            super.paintBorder(c, g, x, y, width, height);
        }
    }

    class PatientContainerPane extends JPanel {

        private final GridBagConstraints constraint = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1,
            0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
        private final Component filler = Box.createRigidArea(new Dimension(5, 5));

        public PatientContainerPane() {
            modelPatient.removeAllElements();
            // do not use addElement
            // modelPatient.insertElementAt(ALL_PATIENTS, 0);
            setLayout(new GridBagLayout());
        }

        List<PatientPane> getPatientPaneList() {
            ArrayList<PatientPane> patientPaneList = new ArrayList<PatientPane>();
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    patientPaneList.add((PatientPane) c);
                }
            }
            return patientPaneList;
        }

        private void refreshLayout() {
            List<PatientPane> list = getPatientPaneList();
            super.removeAll();
            for (PatientPane p : list) {
                p.refreshLayout();
                if (p.getComponentCount() > 0) {
                    addPane(p);
                }
            }
        }

        private void showAllPatients() {
            super.removeAll();
            for (PatientPane patientPane : patientPaneList) {
                patientPane.showTitle(true);
                patientPane.showAllstudies();
                if (patientPane.getComponentCount() > 0) {
                    addPane(patientPane);
                }
            }
            this.revalidate();
        }

        public void addPane(PatientPane patientPane, int position) {
            boolean vertical = true;
            // boolean vertical = ToolWindowAnchor.RIGHT.equals(getAnchor()) ||
            // ToolWindowAnchor.LEFT.equals(getAnchor());
            constraint.gridx = vertical ? 0 : position;
            constraint.gridy = vertical ? position : 0;

            remove(filler);
            constraint.weightx = vertical ? 1.0 : 0.0;
            constraint.weighty = vertical ? 0.0 : 1.0;
            add(patientPane, constraint);
            constraint.weightx = vertical ? 0.0 : 1.0;
            constraint.weighty = vertical ? 1.0 : 0.0;
            add(filler, constraint);
        }

        public void addPane(PatientPane patientPane) {
            addPane(patientPane, GridBagConstraints.RELATIVE);
        }

        public boolean isPatientVisible(MediaSeriesGroup patient) {
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    if (((PatientPane) c).isPatient(patient)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isStudyVisible(MediaSeriesGroup study) {
            MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    PatientPane patientPane = (PatientPane) c;
                    if (patientPane.isPatient(patient) && patientPane.isStudyVisible(study)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
            for (Component c : this.getComponents()) {
                if (c instanceof PatientPane) {
                    PatientPane patientPane = (PatientPane) c;
                    if (patientPane.isPatient(patient) && patientPane.isSeriesVisible(series)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    class PatientPane extends JPanel {

        private final MediaSeriesGroup patient;
        private final GridBagConstraints constraint = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1,
            0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

        public PatientPane(MediaSeriesGroup patient) {
            if (patient == null) {
                throw new IllegalArgumentException("Patient cannot be null"); //$NON-NLS-1$
            }
            this.patient = patient;
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            this.setFocusable(false);
            setLayout(new GridBagLayout());
        }

        public void showTitle(boolean show) {
            if (show) {
                TitleBorder title = new TitleBorder(patient.toString());
                title.setTitleFont(FontTools.getFont12Bold());
                title.setTitleJustification(TitledBorder.LEFT);
                Color color = javax.swing.UIManager.getColor("ComboBox.buttonHighlight"); //$NON-NLS-1$
                title.setTitleColor(color);
                title.setBorder(BorderFactory.createLineBorder(color, 2));
                this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 5, 25, 5), title));
            } else {
                this.setBorder(null);
            }
        }

        public boolean isStudyVisible(MediaSeriesGroup study) {
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    StudyPane studyPane = (StudyPane) c;
                    if (studyPane.isStudy(study)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            MediaSeriesGroup study = model.getParent(series, DicomModel.study);
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    StudyPane studyPane = (StudyPane) c;
                    if (studyPane.isStudy(study) && studyPane.isSeriesVisible(series)) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<StudyPane> getStudyPaneList() {
            ArrayList<StudyPane> studyPaneList = new ArrayList<StudyPane>();
            for (Component c : this.getComponents()) {
                if (c instanceof StudyPane) {
                    studyPaneList.add((StudyPane) c);
                }
            }
            return studyPaneList;
        }

        private void refreshLayout() {
            List<StudyPane> studies = getStudyPaneList();
            super.removeAll();
            for (StudyPane studyPane : studies) {
                studyPane.refreshLayout();
                if (studyPane.getComponentCount() > 0) {
                    addPane(studyPane);
                }
                studyPane.doLayout();
            }
            this.revalidate();
        }

        private void showAllstudies() {
            super.removeAll();
            List<StudyPane> studies = patient2study.get(patient);
            if (studies != null) {
                for (StudyPane studyPane : studies) {
                    studyPane.showAllSeries();
                    studyPane.refreshLayout();
                    if (studyPane.getComponentCount() > 0) {
                        addPane(studyPane);
                    }
                    studyPane.doLayout();
                }
                this.revalidate();
            }
        }

        public void addPane(StudyPane studyPane) {
            addPane(studyPane, GridBagConstraints.RELATIVE);
        }

        public void addPane(StudyPane studyPane, int position) {
            boolean vertical = true;
            // boolean vertical = ToolWindowAnchor.RIGHT.equals(getAnchor()) ||
            // ToolWindowAnchor.LEFT.equals(getAnchor());
            constraint.gridx = vertical ? 0 : position;
            constraint.gridy = vertical ? position : 0;

            constraint.weightx = vertical ? 1.0 : 0.0;
            constraint.weighty = 0.0;

            add(studyPane, constraint);
        }

        public boolean isPatient(MediaSeriesGroup patient) {
            return this.patient.equals(patient);
        }
    }

    class StudyPane extends JPanel {

        final MediaSeriesGroup dicomStudy;
        private final TitleBorder title;

        public StudyPane(MediaSeriesGroup dicomStudy) {
            if (dicomStudy == null) {
                throw new IllegalArgumentException("Study cannot be null"); //$NON-NLS-1$
            }
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            this.dicomStudy = dicomStudy;
            title = new TitleBorder(dicomStudy.toString());
            title.setTitleFont(FontTools.getFont12());
            title.setTitleJustification(TitledBorder.LEFT);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5), title));
            this.setFocusable(false);
            refreshLayout();
        }

        public boolean isSeriesVisible(MediaSeriesGroup series) {
            for (Component c : this.getComponents()) {
                if (c instanceof SeriesPane) {
                    if (((SeriesPane) c).isSeries(series)) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<SeriesPane> getSeriesPaneList() {
            ArrayList<SeriesPane> seriesPaneList = new ArrayList<SeriesPane>();
            for (Component c : this.getComponents()) {
                if (c instanceof SeriesPane) {
                    seriesPaneList.add((SeriesPane) c);
                }
            }
            return seriesPaneList;
        }

        private void refreshLayout() {
            boolean vertical = true;
            // boolean vertical = ToolWindowAnchor.RIGHT.equals(getAnchor()) ||
            // ToolWindowAnchor.LEFT.equals(getAnchor());
            this.setLayout(vertical ? new WrapLayout(FlowLayout.LEFT) : new BoxLayout(this, BoxLayout.X_AXIS));
        }

        private void showAllSeries() {
            super.removeAll();
            List<SeriesPane> seriesList = study2series.get(dicomStudy);
            if (seriesList != null) {
                int thumbnailSize = slider.getValue();
                for (int i = 0; i < seriesList.size(); i++) {
                    SeriesPane series = seriesList.get(i);
                    series.updateSize(thumbnailSize);
                    addPane(series, i);
                }
                this.revalidate();
            }
        }

        public void addPane(SeriesPane seriesPane, int index) {
            seriesPane.updateSize(slider.getValue());
            add(seriesPane, index);
            updateText();
        }

        public void updateText() {
            title.setTitle(dicomStudy.toString());
        }

        public boolean isStudy(MediaSeriesGroup dicomStudy) {
            return this.dicomStudy.equals(dicomStudy);
        }
    }

    class SeriesPane extends JPanel {

        final MediaSeriesGroup sequence;
        private final JLabel label;

        public SeriesPane(MediaSeriesGroup sequence) {
            if (sequence == null) {
                throw new IllegalArgumentException("Series cannot be null"); //$NON-NLS-1$
            }
            // To handle selection color with all L&Fs
            this.setUI(new javax.swing.plaf.PanelUI() {
            });
            this.setOpaque(true);
            this.setBackground(JMVUtils.TREE_BACKROUND);
            this.sequence = sequence;
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            int thumbnailSize = slider.getValue();
            if (sequence instanceof Series) {
                Series series = (Series) sequence;
                Thumbnail thumb = (Thumbnail) series.getTagValue(TagW.Thumbnail);
                if (thumb == null) {
                    thumb = createThumbnail(series, model, thumbnailSize);
                    series.setTag(TagW.Thumbnail, thumb);
                }
                this.add(thumb);
            }
            this.setAlignmentX(LEFT_ALIGNMENT);
            this.setAlignmentY(TOP_ALIGNMENT);
            String desc = (String) sequence.getTagValue(TagW.SeriesDescription);
            label = new JLabel(desc == null ? "" : desc, SwingConstants.CENTER); //$NON-NLS-1$
            label.setFont(FontTools.getFont10());
            label.setFocusable(false);
            this.setFocusable(false);
            updateSize(thumbnailSize);
            this.add(label);

        }

        public void updateSize(int thumbnailSize) {
            Dimension max = label.getMaximumSize();
            if (max == null || max.width != thumbnailSize) {
                if (sequence instanceof Series) {
                    Series series = (Series) sequence;
                    SeriesThumbnail thumb = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                    if (thumb != null) {
                        thumb.setThumbnailSize(thumbnailSize);
                    }
                }
                FontRenderContext frc = new FontRenderContext(null, false, false);
                Dimension dim =
                    new Dimension(thumbnailSize, (int) (label.getFont().getStringBounds("0", frc).getHeight() + 1.0f)); //$NON-NLS-1$
                label.setPreferredSize(dim);
                label.setMaximumSize(dim);
            }
        }

        public void updateText() {
            String desc = (String) sequence.getTagValue(TagW.SeriesDescription);
            label.setText(desc == null ? "" : desc); //$NON-NLS-1$
        }

        public boolean isSeries(MediaSeriesGroup sequence) {
            return this.sequence.equals(sequence);
        }

        public MediaSeriesGroup getSequence() {
            return sequence;
        }

    }

    /**
     * @return
     */
    protected JPanel getPanel() {
        if (panel_1 == null) {
            panel_1 = new JPanel();
            panel = new JPanel();
            final GridBagLayout gridBagLayout = new GridBagLayout();
            gridBagLayout.rowHeights = new int[] { 0, 0, 7 };
            panel.setLayout(gridBagLayout);
            panel.setBorder(new EmptyBorder(5, 5, 5, 5));

            final JLabel label = new JLabel(PATIENT_ICON);
            final GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.insets = new Insets(0, 0, 5, 5);
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            panel.add(label, gridBagConstraints);

            final GridBagConstraints gridBagConstraints_1 = new GridBagConstraints();
            gridBagConstraints_1.insets = new Insets(0, 2, 5, 0);
            gridBagConstraints_1.anchor = GridBagConstraints.WEST;
            gridBagConstraints_1.weightx = 1.0;
            gridBagConstraints_1.gridy = 0;
            gridBagConstraints_1.gridx = 1;
            panel.add(patientCombobox, gridBagConstraints_1);
            patientCombobox.setMaximumRowCount(15);
            patientCombobox.setFont(FontTools.getFont11());
            JMVUtils.setPreferredWidth(patientCombobox, 145, 145);
            // Update UI before adding the Tooltip feature in the combobox list
            patientCombobox.updateUI();
            patientCombobox.addItemListener(patientChangeListener);
            JMVUtils.addTooltipToComboList(patientCombobox);

            final GridBagConstraints gridBagConstraints_3 = new GridBagConstraints();
            gridBagConstraints_3.anchor = GridBagConstraints.WEST;
            gridBagConstraints_3.insets = new Insets(2, 2, 5, 0);
            gridBagConstraints_3.gridx = 1;
            gridBagConstraints_3.gridy = 1;

            panel.add(studyCombobox, gridBagConstraints_3);
            studyCombobox.setMaximumRowCount(15);
            studyCombobox.setFont(FontTools.getFont11());
            // Update UI before adding the Tooltip feature in the combobox list
            studyCombobox.updateUI();
            JMVUtils.setPreferredWidth(studyCombobox, 145, 145);
            // do not use addElement
            modelStudy.insertElementAt(ALL_STUDIES, 0);
            modelStudy.setSelectedItem(ALL_STUDIES);
            studyCombobox.addItemListener(studyItemListener);
            JMVUtils.addTooltipToComboList(studyCombobox);

            final GridBagConstraints gridBagConstraints_4 = new GridBagConstraints();
            gridBagConstraints_4.anchor = GridBagConstraints.WEST;
            gridBagConstraints_4.insets = new Insets(2, 2, 5, 0);
            gridBagConstraints_4.gridx = 1;
            gridBagConstraints_4.gridy = 2;

            panel.add(koOpen, gridBagConstraints_4);
            koOpen.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    final MediaSeriesGroup patient = selectedPatient == null ? null : selectedPatient.patient;
                    if (patient != null && e.getSource() instanceof JButton) {
                        List<KOSpecialElement> list = DicomModel.getSpecialElements(patient, KOSpecialElement.class);
                        if (list != null && list.size() > 0) {
                            JButton button = (JButton) e.getSource();

                            if (list.size() == 1) {
                                model.openrelatedSeries(list.get(0), patient);
                            } else {
                                Collections.sort(list, DicomSpecialElement.ORDER_BY_DATE);

                                JPopupMenu popupMenu = new JPopupMenu();
                                popupMenu.add(new TitleMenuItem(ActionW.KO_SELECTION.getTitle(), popupMenu.getInsets()));
                                popupMenu.addSeparator();

                                ButtonGroup group = new ButtonGroup();

                                for (final KOSpecialElement koSpecialElement : list) {
                                    final JMenuItem item = new JMenuItem(koSpecialElement.getShortLabel());
                                    item.addActionListener(new ActionListener() {

                                        @Override
                                        public void actionPerformed(ActionEvent e) {
                                            model.openrelatedSeries(koSpecialElement, patient);
                                        }
                                    });
                                    popupMenu.add(item);
                                    group.add(item);
                                }
                                popupMenu.show(button, 5, 5);
                            }
                        }
                    }
                }

            });
            koOpen.setVisible(false);

            panel_1.setLayout(new BorderLayout());
            panel_1.add(panel, BorderLayout.NORTH);

            if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.explorer.moreoptions", true)) { //$NON-NLS-1$
                GridBagConstraints gbc_btnMoreOptions = new GridBagConstraints();
                gbc_btnMoreOptions.anchor = GridBagConstraints.EAST;
                gbc_btnMoreOptions.gridx = 1;
                gbc_btnMoreOptions.gridy = 3;
                btnMoreOptions.setFont(FontTools.getFont10());
                btnMoreOptions.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (btnMoreOptions.isSelected()) {
                            panel_1.add(panel_2);
                        } else {
                            panel_1.remove(panel_2);
                        }
                        panel_1.revalidate();
                        panel_1.repaint();
                    }
                });
                panel.add(btnMoreOptions, gbc_btnMoreOptions);
            }

            // panel_1.add(panel_2, BorderLayout.SOUTH);
            GridBagLayout gbl_panel_2 = new GridBagLayout();
            panel_2.setLayout(gbl_panel_2);

            GridBagConstraints gbc_panel_3 = new GridBagConstraints();
            gbc_panel_3.weightx = 1.0;
            gbc_panel_3.anchor = GridBagConstraints.NORTHWEST;
            gbc_panel_3.gridx = 0;
            gbc_panel_3.gridy = 0;
            FlowLayout flowLayout = (FlowLayout) panel_3.getLayout();
            flowLayout.setHgap(10);
            flowLayout.setAlignment(FlowLayout.LEFT);
            panel_2.add(panel_3, gbc_panel_3);

            btnImport.setText(Messages.getString("DicomExplorer.import"));//$NON-NLS-1$
            panel_3.add(btnImport);
            btnExport.setText(Messages.getString("DicomExplorer.export"));//$NON-NLS-1$
            panel_3.add(btnExport);

            final JPanel palenSlider1 = new JPanel();
            palenSlider1.setLayout(new BoxLayout(palenSlider1, BoxLayout.Y_AXIS));
            palenSlider1.setBorder(new TitledBorder(
                Messages.getString("DicomExplorer.thmb_size") + " " + slider.getValue())); //$NON-NLS-1$ //$NON-NLS-2$

            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setMajorTickSpacing(16);
            slider.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent e) {
                    JSlider source = (JSlider) e.getSource();
                    if (!source.getValueIsAdjusting()) {
                        ((TitledBorder) palenSlider1.getBorder()).setTitle(Messages
                            .getString("DicomExplorer.thmb_size") + StringUtil.COLON_AND_SPACE + source.getValue()); //$NON-NLS-1$ 
                        palenSlider1.repaint();
                        updateThumbnailSize();
                    }

                }
            });
            JMVUtils.setPreferredWidth(slider, 145, 145);
            palenSlider1.add(slider);
            GridBagConstraints gbc_palenSlider1 = new GridBagConstraints();
            gbc_palenSlider1.insets = new Insets(0, 5, 5, 5);
            gbc_palenSlider1.anchor = GridBagConstraints.NORTHWEST;
            gbc_palenSlider1.gridx = 0;
            gbc_palenSlider1.gridy = 1;
            panel_2.add(palenSlider1, gbc_palenSlider1);
        }
        return panel_1;
    }

    public boolean isPatientHasOpenSeries(MediaSeriesGroup p) {

        Collection<MediaSeriesGroup> studies = model.getChildren(p);
        synchronized (model) {
            for (Iterator<MediaSeriesGroup> iterator = studies.iterator(); iterator.hasNext();) {
                MediaSeriesGroup study = iterator.next();
                Collection<MediaSeriesGroup> seriesList = model.getChildren(study);
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup seq = it.next();
                    if (seq instanceof Series) {
                        Boolean open = (Boolean) ((Series) seq).getTagValue(TagW.SeriesOpen);
                        return open == null ? false : open;
                    }
                }
            }
        }
        return false;
    }

    public void selectPatient(MediaSeriesGroup patient) {
        if (selectedPatient == null || patient != selectedPatient.patient) {
            selectionList.clear();
            studyCombobox.removeItemListener(studyItemListener);
            modelStudy.removeAllElements();
            // do not use addElement
            modelStudy.insertElementAt(ALL_STUDIES, 0);
            modelStudy.setSelectedItem(ALL_STUDIES);
            patientContainer.removeAll();
            if (patient == null) {
                selectedPatient = null;
                patientContainer.showAllPatients();
            } else {
                selectedPatient = createPatientPane(patient);
                selectedPatient.showTitle(false);
                List<StudyPane> studies = patient2study.get(patient);
                if (studies != null) {
                    for (StudyPane studyPane : studies) {
                        modelStudy.addElement(studyPane.dicomStudy);
                    }
                }
                studyCombobox.addItemListener(studyItemListener);
                selectStudy();
                patientContainer.addPane(selectedPatient);
                koOpen.setVisible(DicomModel.hasSpecialElements(patient, KOSpecialElement.class));
                // Send message for selecting related plug-ins window
                model.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.Select, model, null, patient));
            }
        }
    }

    private List<Series> getSplitSeries(Series dcm) {
        MediaSeriesGroup study = model.getParent(dcm, DicomModel.study);
        Object splitNb = dcm.getTagValue(TagW.SplitSeriesNumber);
        List<Series> list = new ArrayList<Series>();
        if (splitNb == null || study == null) {
            list.add(dcm);
            return list;
        }
        String uid = (String) dcm.getTagValue(TagW.SeriesInstanceUID);
        if (uid != null) {
            Collection<MediaSeriesGroup> seriesList = model.getChildren(study);
            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                MediaSeriesGroup group = it.next();
                if (group instanceof Series) {
                    Series s = (Series) group;
                    if (uid.equals(s.getTagValue(TagW.SeriesInstanceUID))) {
                        list.add(s);
                    }
                }
            }
        }
        return list;
    }

    private void updateSplitSeries(Series dcmSeries) {
        MediaSeriesGroup study = model.getParent(dcmSeries, DicomModel.study);
        // In case the Series has been replaced (split number = -1) and removed
        if (study == null) {
            return;
        }
        StudyPane studyPane = createStudyPaneInstance(study, null);
        List<Series> list = getSplitSeries(dcmSeries);

        List<SeriesPane> seriesList = study2series.get(study);
        if (seriesList == null) {
            seriesList = new ArrayList<SeriesPane>();
            study2series.put(study, seriesList);
        }
        boolean addSeries = patientContainer.isStudyVisible(study);
        boolean repaintStudy = false;
        for (Series dicomSeries : list) {
            int[] positionSeries = new int[1];
            createSeriesPaneInstance(dicomSeries, positionSeries);
            if (addSeries && positionSeries[0] != -1) {
                repaintStudy = true;
            }

            SeriesThumbnail thumb = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (thumb != null) {
                thumb.reBuildThumbnail();
            }
        }

        Integer nb = (Integer) dcmSeries.getTagValue(TagW.SplitSeriesNumber);
        // Convention -> split number inferior to 0 is a Series that has been replaced (ex. when a DicomSeries is
        // converted DicomVideoSeries after downloading files).
        if (nb != null && nb < 0) {
            model.removeSeries(dcmSeries);
            repaintStudy = true;
        }
        if (repaintStudy) {
            studyPane.removeAll();
            for (int i = 0, k = 1; i < seriesList.size(); i++) {
                SeriesPane s = seriesList.get(i);
                studyPane.addPane(s, i);
                if (list.contains(s.getSequence())) {
                    s.getSequence().setTag(TagW.SplitSeriesNumber, k);
                    k++;
                }
            }

            studyPane.revalidate();
            studyPane.repaint();
        } else {
            int k = 1;
            for (SeriesPane s : seriesList) {
                if (list.contains(s.getSequence())) {
                    s.getSequence().setTag(TagW.SplitSeriesNumber, k);
                    k++;
                }
            }
        }

    }

    private void selectStudy() {
        Object item = modelStudy.getSelectedItem();
        if (item instanceof MediaSeriesGroupNode) {
            MediaSeriesGroupNode selectedStudy = (MediaSeriesGroupNode) item;
            selectStudy(selectedStudy);
        } else {
            selectStudy(null);
        }
    }

    public void selectStudy(MediaSeriesGroup selectedStudy) {
        if (selectedPatient != null) {
            selectionList.clear();
            selectedPatient.removeAll();

            if (selectedStudy == null) {
                selectedPatient.showAllstudies();
            } else {
                StudyPane studyPane = getStudyPane(selectedStudy);
                if (studyPane != null) {
                    studyPane.showAllSeries();
                    studyPane.refreshLayout();
                    selectedPatient.addPane(studyPane);
                    studyPane.doLayout();
                }
            }
            selectedPatient.revalidate();
            selectedPatient.repaint();
        }
    }

    @Override
    public void dispose() {
        super.closeDockable();

    }

    private void addDicomSeries(Series series) {
        if (DicomModel.isSpecialModality(series)) {
            // Up to now nothing has to be done in the explorer view about specialModality

            // model.addSpecialModality(series);
            // addSpecialModalityToStudy(series);
            return;
        }
        LOGGER.info("Add series: {}", series); //$NON-NLS-1$
        MediaSeriesGroup study = model.getParent(series, DicomModel.study);
        MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
        PatientPane patientPane = createPatientPane(patient);

        if (modelPatient.getIndexOf(patient) < 0) {
            modelPatient.addElement(patient);
            if (modelPatient.getSize() == 1) {
                selectedPatient = patientPane;
                patientContainer.addPane(selectedPatient);
                patientCombobox.removeItemListener(patientChangeListener);
                patientCombobox.setSelectedItem(patient);
                patientCombobox.addItemListener(patientChangeListener);
            }
            // Mode all patients
            else if (selectedPatient == null) {
                patientContainer.addPane(patientPane);
            }
        }
        boolean addSeries = selectedPatient == patientPane;
        List<StudyPane> studies = patient2study.get(patient);
        if (studies == null) {
            studies = new ArrayList<StudyPane>();
            patient2study.put(patient, studies);
        }
        Object selectedStudy = modelStudy.getSelectedItem();
        int[] positionStudy = new int[1];
        StudyPane studyPane = createStudyPaneInstance(study, positionStudy);

        List<SeriesPane> seriesList = study2series.get(study);
        if (seriesList == null) {
            seriesList = new ArrayList<SeriesPane>();
            study2series.put(study, seriesList);
        }

        int[] positionSeries = new int[1];
        createSeriesPaneInstance(series, positionSeries);
        if (addSeries && positionSeries[0] != -1) {
            koOpen.setVisible(DicomModel.hasSpecialElements(patient, KOSpecialElement.class));
            // If new study
            if (positionStudy[0] != -1) {
                if (modelStudy.getIndexOf(study) < 0) {
                    modelStudy.addElement(study);
                }
                // if modelStudy has the value "All studies"
                if (ALL_STUDIES.equals(selectedStudy)) {
                    patientPane.removeAll();
                    for (StudyPane s : studies) {
                        patientPane.addPane(s);
                    }
                    patientPane.revalidate();
                }
            }
            if (patientPane.isStudyVisible(study)) {
                studyPane.removeAll();
                for (int i = 0; i < seriesList.size(); i++) {
                    studyPane.addPane(seriesList.get(i), i);
                }
                studyPane.revalidate();
                studyPane.repaint();
            }
        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.SELECT_VIEW.equals(type) && event.getSeriesViewer() instanceof ImageViewerPlugin) {
            DefaultView2d<?> pane = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
            if (pane != null) {
                MediaSeries s = pane.getSeries();
                if (s != null) {
                    if (!getSelectionList().isOpenningSeries() && patientContainer.isSeriesVisible(s)) {
                        SeriesPane p = getSeriesPane(s);
                        if (p != null) {
                            JViewport vp = thumnailView.getViewport();
                            Point pt2 =
                                SwingUtilities.convertPoint(p, new Point(0, p.getHeight() / 2), patientContainer);
                            pt2.x = vp.getViewPosition().x;
                            pt2.y -= vp.getHeight() / 2;
                            int maxHeight = (int) (vp.getViewSize().getHeight() - vp.getExtentSize().getHeight());
                            if (pt2.y < 0) {
                                pt2.y = 0;
                            } else if (pt2.y > maxHeight) {
                                pt2.y = maxHeight;
                            }
                            vp.setViewPosition(pt2);
                            // Clear the selection when another view is selected
                            getSelectionList().clear();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Get only event from the model of DicomExplorer
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            ObservableEvent.BasicAction action = event.getActionCommand();
            Object newVal = event.getNewValue();
            if (model.equals(evt.getSource())) {
                if (ObservableEvent.BasicAction.Select.equals(action)) {
                    if (newVal instanceof Series) {
                        Series dcm = (Series) newVal;
                        MediaSeriesGroup patient = model.getParent(dcm, DicomModel.patient);
                        if (!isSelectedPatient(patient)) {
                            modelPatient.setSelectedItem(patient);
                        }
                    }
                } else if (ObservableEvent.BasicAction.Add.equals(action)) {
                    if (newVal instanceof Series) {
                        addDicomSeries((Series) newVal);
                    }
                } else if (ObservableEvent.BasicAction.Remove.equals(action)) {
                    if (newVal instanceof MediaSeriesGroup) {
                        MediaSeriesGroup group = (MediaSeriesGroup) newVal;
                        // Patient Group
                        if (TagW.PatientPseudoUID.equals(group.getTagID())) {
                            removePatientPane(group);
                        }
                        // Study Group
                        else if (TagW.StudyInstanceUID.equals(group.getTagID())) {
                            removeStudy(group);
                        }
                        // Series Group
                        else if (TagW.SubseriesInstanceUID.equals(group.getTagID())) {
                            removeSeries(group);
                        }
                    }
                }
                // Update patient and study infos from the series (when receiving the first downloaded image)
                else if (ObservableEvent.BasicAction.UpdateParent.equals(action)) {
                    if (newVal instanceof Series) {
                        Series dcm = (Series) newVal;
                        MediaSeriesGroup patient = model.getParent(dcm, DicomModel.patient);
                        if (isSelectedPatient(patient)) {
                            MediaSeriesGroup study = model.getParent(dcm, DicomModel.study);
                            StudyPane studyPane = getStudyPane(study);
                            if (studyPane != null) {
                                if (!DicomModel.isSpecialModality(dcm)) {
                                    SeriesPane pane = getSeriesPane(dcm);
                                    if (pane != null) {
                                        pane.updateText();
                                    }
                                }
                                studyPane.updateText();
                            }
                        }
                    }
                }
                // update
                else if (ObservableEvent.BasicAction.Update.equals(action)) {
                    if (newVal instanceof Series) {
                        Series dcm = (Series) newVal;
                        Integer splitNb = (Integer) dcm.getTagValue(TagW.SplitSeriesNumber);
                        if (splitNb != null) {
                            updateSplitSeries(dcm);
                        }
                    }
                } else if (ObservableEvent.BasicAction.LoadingStart.equals(action)) {
                    if (newVal instanceof ExplorerTask) {
                        addTaskToGlobalProgression((ExplorerTask) newVal);
                    }
                } else if (ObservableEvent.BasicAction.LoadingStop.equals(action)) {
                    if (newVal instanceof ExplorerTask) {
                        removeTaskToGlobalProgression((ExplorerTask) newVal);
                    }
                }
            } else if (evt.getSource() instanceof SeriesViewer) {
                if (ObservableEvent.BasicAction.Select.equals(action)) {
                    if (newVal instanceof MediaSeriesGroup) {
                        MediaSeriesGroup patient = (MediaSeriesGroup) newVal;
                        if (!isSelectedPatient(patient)) {
                            modelPatient.setSelectedItem(patient);
                            // focus get back to viewer
                            if (evt.getSource() instanceof ViewerPlugin) {
                                ((ViewerPlugin) evt.getSource()).requestFocusInWindow();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Icon getIcon() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUIName() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public DataExplorerModel getDataExplorerModel() {
        return model;
    }

    @Override
    protected void changeToolWindowAnchor(CLocation clocation) {
        removeAll();
        boolean vertical = true;
        // boolean vertical = ToolWindowAnchor.RIGHT.equals(anchor) || ToolWindowAnchor.LEFT.equals(anchor);
        add(getPanel(), vertical ? BorderLayout.NORTH : BorderLayout.WEST);
        patientContainer.refreshLayout();
        // ToolWindow win = getToolWindow();
        // if (win != null) {
        // DockedTypeDescriptor dockedTypeDescriptor =
        // (DockedTypeDescriptor) win.getTypeDescriptor(ToolWindowType.DOCKED);
        // int width = this.getDockableWidth();
        // if (width > 0) {
        // dockedTypeDescriptor.setDockLength(vertical ? width : width + 15);
        // }
        // }
        add(thumnailView, BorderLayout.CENTER);
        if (tasks.size() > 0) {
            add(getLoadingPanel(), vertical ? BorderLayout.SOUTH : BorderLayout.EAST);
        }

    }

    private JPanel getLoadingPanel() {
        if (panel_4 == null) {
            panel_4 = new JPanel();

            globalResumeButton.setToolTipText(Messages.getString("DicomExplorer.resume_all")); //$NON-NLS-1$
            globalResumeButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    DownloadManager.resume();
                }
            });
            panel_4.add(globalResumeButton);
            globalStopButton.setToolTipText(Messages.getString("DicomExplorer.stop_all")); //$NON-NLS-1$
            globalStopButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    DownloadManager.stop();
                }
            });
            panel_4.add(globalStopButton);
            panel_4.add(globalProgress);
            panel_4.add(globalLoadingLabel);
        }
        return panel_4;
    }

    public synchronized void addTaskToGlobalProgression(final ExplorerTask task) {
        if (!tasks.contains(task)) {
            tasks.add(task);
            if (tasks.size() > 0) {
                GuiExecutor.instance().invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        JPanel loadingPanel = getLoadingPanel();
                        globalLoadingLabel.setText(task.getMessage());
                        if (globalProgress.isVisible()) {
                            if (task.isInterruptible()) {
                                globalProgress.setVisible(false);
                                globalResumeButton.setVisible(true);
                                globalStopButton.setVisible(true);
                            } else {
                                globalResumeButton.setVisible(false);
                                globalStopButton.setVisible(false);
                            }
                        }

                        if (getComponentZOrder(loadingPanel) == -1) {
                            if (globalProgress.isVisible()) {
                                globalProgress.setIndeterminate(true);
                            }
                            boolean vertical = true;
                            add(loadingPanel, vertical ? BorderLayout.SOUTH : BorderLayout.EAST);
                            revalidate();
                            repaint();
                        }
                    }
                });
            }
        }
        add(thumnailView, BorderLayout.CENTER);
        if (tasks.size() > 0) {
            add(getLoadingPanel(), true ? BorderLayout.SOUTH : BorderLayout.EAST);
        }

    }

    public synchronized void removeTaskToGlobalProgression(ExplorerTask task) {
        if (task != null) {
            tasks.remove(task);
            if (tasks.size() == 0) {
                GuiExecutor.instance().invokeAndWait(new Runnable() {

                    @Override
                    public void run() {
                        remove(getLoadingPanel());
                        globalProgress.setIndeterminate(false);
                        globalProgress.setVisible(true);
                        revalidate();
                        repaint();
                    }
                });

            }
        }
    }

    public static SeriesThumbnail createThumbnail(final Series series, final DicomModel dicomModel,
        final int thumbnailSize) {

        Callable callable = new Callable<SeriesThumbnail>() {

            @Override
            public SeriesThumbnail call() throws Exception {
                final SeriesThumbnail thumb = new SeriesThumbnail(series, thumbnailSize);
                if (series.getSeriesLoader() instanceof LoadSeries) {
                    // In case series is downloaded or canceled
                    LoadSeries loader = (LoadSeries) series.getSeriesLoader();
                    thumb.setProgressBar(loader.isDone() ? null : loader.getProgressBar());
                }
                thumb.registerListeners();
                thumb.addMouseListener(createThumbnailMouseAdapter(series, dicomModel, null));
                thumb.addKeyListener(createThumbnailKeyListener(series, dicomModel));
                return thumb;
            }
        };
        FutureTask<SeriesThumbnail> future = new FutureTask<SeriesThumbnail>(callable);
        GuiExecutor.instance().invokeAndWait(future);
        SeriesThumbnail result = null;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static MouseAdapter createThumbnailMouseAdapter(final Series series, final DicomModel dicomModel,
        final LoadSeries loadSeries) {
        return new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    final SeriesSelectionModel selList = getSeriesSelectionModel();
                    selList.setOpenningSeries(true);
                    Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
                    props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, true);
                    props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
                    props.put(ViewerPluginBuilder.OPEN_IN_SELECTION, true);

                    String mime = series.getMimeType();
                    SeriesViewerFactory plugin = UIManager.getViewerFactory(mime);
                    if (plugin == null) {
                        plugin = DefaultMimeAppFactory.getInstance();
                    }

                    ArrayList<MediaSeries<? extends MediaElement<?>>> list =
                        new ArrayList<MediaSeries<? extends MediaElement<?>>>(1);
                    list.add(series);
                    ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, list, dicomModel, props);
                    ViewerPluginBuilder.openSequenceInPlugin(builder);

                    selList.setOpenningSeries(false);
                }
            }

            @Override
            public void mousePressed(MouseEvent mouseevent) {
                final Component c = mouseevent.getComponent();
                if (!c.isFocusOwner()) {
                    c.requestFocusInWindow();
                }
                DataExplorerView explorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
                final SeriesSelectionModel selList = getSeriesSelectionModel();

                if (SwingUtilities.isRightMouseButton(mouseevent)) {
                    JPopupMenu popupMenu = new JPopupMenu();

                    List<SeriesViewerFactory> plugins =
                        UIManager.getViewerFactoryList(new String[] { series.getMimeType() });
                    if (!selList.contains(series)) {
                        selList.setSelectionInterval(series, series);
                    }
                    // Is the selection has multiple mime types
                    boolean multipleMimes = false;
                    String mime = series.getMimeType();
                    for (Series s : selList) {
                        if (!mime.equals(s.getMimeType())) {
                            multipleMimes = true;
                            break;
                        }
                    }
                    final List<MediaSeries<? extends MediaElement<?>>> seriesList;
                    if (multipleMimes) {
                        // Filter the list to have only one mime type
                        seriesList = new ArrayList<MediaSeries<? extends MediaElement<?>>>();
                        for (Series s : selList) {
                            if (mime.equals(s.getMimeType())) {
                                seriesList.add(s);
                            }
                        }
                    } else {
                        seriesList = new ArrayList<MediaSeries<? extends MediaElement<?>>>(selList);
                    }
                    for (final SeriesViewerFactory viewerFactory : plugins) {
                        JMenu menuFactory = new JMenu(viewerFactory.getUIName());
                        menuFactory.setIcon(viewerFactory.getIcon());

                        JMenuItem item4 = new JMenuItem(Messages.getString("DicomExplorer.open")); //$NON-NLS-1$
                        item4.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                selList.setOpenningSeries(true);
                                ViewerPluginBuilder.openSequenceInPlugin(viewerFactory, seriesList, dicomModel, true,
                                    true);
                                selList.setOpenningSeries(false);
                            }
                        });
                        menuFactory.add(item4);

                        // Exclude system factory
                        if (viewerFactory.canExternalizeSeries()) {
                            item4 = new JMenuItem(Messages.getString("DicomExplorer.open_win")); //$NON-NLS-1$
                            item4.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    selList.setOpenningSeries(true);
                                    ViewerPluginBuilder.openSequenceInPlugin(viewerFactory, seriesList, dicomModel,
                                        false, true);
                                    selList.setOpenningSeries(false);
                                }
                            });
                            menuFactory.add(item4);
                        }

                        if (viewerFactory.canAddSeries()) {
                            item4 = new JMenuItem(Messages.getString("DicomExplorer.add")); //$NON-NLS-1$
                            item4.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    selList.setOpenningSeries(true);
                                    ViewerPluginBuilder.openSequenceInPlugin(viewerFactory, seriesList, dicomModel,
                                        true, false);
                                    selList.setOpenningSeries(false);
                                }
                            });
                            menuFactory.add(item4);
                        }

                        popupMenu.add(menuFactory);

                        if (viewerFactory instanceof MimeSystemAppFactory) {
                            final JMenuItem item5 = new JMenuItem(Messages.getString("DicomExplorer.open_info"), null); //$NON-NLS-1$
                            item5.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    JFrame frame = new JFrame(Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
                                    frame.setSize(500, 630);
                                    DicomFieldsView view = new DicomFieldsView();
                                    view.changingViewContentEvent(new SeriesViewerEvent(viewerFactory
                                        .createSeriesViewer(null), series, series.getMedia(MEDIA_POSITION.FIRST, null,
                                        null), EVENT.SELECT));
                                    JPanel panel = new JPanel();
                                    panel.setLayout(new BorderLayout());
                                    panel.add(view);
                                    frame.getContentPane().add(panel);
                                    frame.setVisible(true);
                                }
                            });
                            popupMenu.add(item5);
                        }
                    }
                    if (series instanceof DicomSeries) {
                        if (selList.size() == 1) {
                            popupMenu.add(new JSeparator());
                            JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series")); //$NON-NLS-1$
                            item2.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {

                                    String fruid = (String) series.getTagValue(TagW.FrameOfReferenceUID);
                                    if (fruid != null) {
                                        selList.clear();
                                        MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
                                        Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(studyGroup);
                                        synchronized (dicomModel) {
                                            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                                                MediaSeriesGroup seq = it.next();
                                                if (seq instanceof Series) {
                                                    Series s = (Series) seq;
                                                    if (fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID))) {
                                                        selList.add(s);
                                                    }
                                                }
                                            }
                                        }
                                        selList.addSelectionInterval(series, series);
                                    }
                                }
                            });
                            popupMenu.add(item2);
                            item2 = new JMenuItem(Messages.getString("DicomExplorer.sel_rel_series_axis")); //$NON-NLS-1$
                            item2.addActionListener(new ActionListener() {

                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    String fruid = (String) series.getTagValue(TagW.FrameOfReferenceUID);
                                    if (fruid != null) {
                                        selList.clear();
                                        MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
                                        Collection<MediaSeriesGroup> seriesList = dicomModel.getChildren(studyGroup);
                                        synchronized (dicomModel) {
                                            for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                                                MediaSeriesGroup seq = it.next();
                                                if (seq instanceof Series) {
                                                    Series s = (Series) seq;
                                                    if (fruid.equals(s.getTagValue(TagW.FrameOfReferenceUID))) {
                                                        if (ImageOrientation.hasSameOrientation(series, s)) {
                                                            selList.add(s);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        selList.addSelectionInterval(series, series);
                                    }
                                }
                            });
                            popupMenu.add(item2);
                        }
                        if (selList.size() == 1 && loadSeries != null) {
                            if (loadSeries.isStopped()) {
                                popupMenu.add(new JSeparator());
                                JMenuItem item3 = new JMenuItem(Messages.getString("LoadSeries.resume")); //$NON-NLS-1$
                                item3.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        loadSeries.resume();
                                    }
                                });
                                popupMenu.add(item3);
                            } else if (!loadSeries.isDone()) {
                                popupMenu.add(new JSeparator());
                                JMenuItem item3 = new JMenuItem(Messages.getString("LoadSeries.stop")); //$NON-NLS-1$
                                item3.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        loadSeries.stop();
                                    }
                                });
                                popupMenu.add(item3);
                            }
                        }

                        Object splitNb = series.getTagValue(TagW.SplitSeriesNumber);
                        if (splitNb != null && seriesList.size() > 1) {
                            String uid = (String) series.getTagValue(TagW.SeriesInstanceUID);
                            boolean sameOrigin = true;
                            if (uid != null) {
                                for (MediaSeries s : seriesList) {
                                    if (!uid.equals(s.getTagValue(TagW.SeriesInstanceUID))) {
                                        sameOrigin = false;
                                        break;
                                    }
                                }
                            }
                            if (sameOrigin) {
                                popupMenu.add(new JSeparator());
                                JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.merge")); //$NON-NLS-1$
                                item2.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        selList.clear();
                                        dicomModel.mergeSeries(seriesList);
                                    }
                                });
                                popupMenu.add(item2);
                            }
                        }
                    }
                    popupMenu.add(new JSeparator());
                    JMenuItem item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_series")); //$NON-NLS-1$
                    item2.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            for (int i = selList.size() - 1; i >= 0; i--) {
                                dicomModel.removeSeries(selList.get(i));
                            }
                            selList.clear();
                        }
                    });
                    popupMenu.add(item2);
                    if (selList.size() == 1) {
                        item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_study")); //$NON-NLS-1$
                        item2.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                MediaSeriesGroup studyGroup = dicomModel.getParent(series, DicomModel.study);
                                dicomModel.removeStudy(studyGroup);
                                selList.clear();
                            }
                        });
                        popupMenu.add(item2);
                        item2 = new JMenuItem(Messages.getString("DicomExplorer.rem_pat")); //$NON-NLS-1$
                        item2.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                MediaSeriesGroup patientGroup =
                                    dicomModel.getParent(dicomModel.getParent(series, DicomModel.study),
                                        DicomModel.patient);
                                dicomModel.removePatient(patientGroup);
                                selList.clear();
                            }
                        });
                        popupMenu.add(item2);
                        if (series.size(null) > 1) {
                            if (series.getMedia(0, null, null) instanceof ImageElement) {
                                popupMenu.add(new JSeparator());
                                JMenu menu = new JMenu(Messages.getString("DicomExplorer.build_thumb")); //$NON-NLS-1$
                                item2 = new JMenuItem(Messages.getString("DicomExplorer.from_1")); //$NON-NLS-1$
                                item2.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                                        if (t != null) {
                                            t.reBuildThumbnail(MEDIA_POSITION.FIRST);
                                        }
                                    }
                                });
                                menu.add(item2);
                                item2 = new JMenuItem(Messages.getString("DicomExplorer.from_mid")); //$NON-NLS-1$
                                item2.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                                        if (t != null) {
                                            t.reBuildThumbnail(MEDIA_POSITION.MIDDLE);
                                        }
                                    }
                                });
                                menu.add(item2);
                                item2 = new JMenuItem(Messages.getString("DicomExplorer.from_last")); //$NON-NLS-1$
                                item2.addActionListener(new ActionListener() {

                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        SeriesThumbnail t = (SeriesThumbnail) series.getTagValue(TagW.Thumbnail);
                                        if (t != null) {
                                            t.reBuildThumbnail(MEDIA_POSITION.LAST);
                                        }
                                    }
                                });
                                menu.add(item2);
                                popupMenu.add(menu);
                            }
                        }
                    }
                    popupMenu.show(mouseevent.getComponent(), mouseevent.getX() - 5, mouseevent.getY() - 5);
                } else {
                    selList.adjustSelection(mouseevent, series);
                }
            }
        };
    }

    public static KeyAdapter createThumbnailKeyListener(final Series series, final DicomModel dicomModel) {
        return new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ENTER) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    if (selList.size() == 0) {
                        selList.add(series);
                    }
                    selList.setOpenningSeries(true);
                    ViewerPluginBuilder.openSequenceInDefaultPlugin(
                        new ArrayList<MediaSeries<? extends MediaElement<?>>>(selList), dicomModel, true, true);
                    selList.setOpenningSeries(false);
                    if (e.getSource() instanceof JComponent) {
                        ((JComponent) e.getSource()).requestFocusInWindow();
                    }
                    e.consume();
                } else if (code == KeyEvent.VK_DOWN) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    selList.adjustSelection(e, selList.getNextElement(series));
                } else if (code == KeyEvent.VK_UP) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    selList.adjustSelection(e, selList.getPreviousElement(series));
                } else if (e.isControlDown() && code == KeyEvent.VK_A) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    selList.setSelectionInterval(selList.getFirstElement(), selList.getLastElement());
                } else if (code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_END) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    Series val = selList.getLastElement();
                    selList.setSelectionInterval(val, val);
                } else if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_HOME) {
                    SeriesSelectionModel selList = getSeriesSelectionModel();
                    Series val = selList.getFirstElement();
                    selList.setSelectionInterval(val, val);
                }
            }

        };
    }

    static SeriesSelectionModel getSeriesSelectionModel() {
        DataExplorerView explorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
        return explorer instanceof DicomExplorer ? ((DicomExplorer) explorer).getSelectionList()
            : new SeriesSelectionModel(null);
    }

    @Override
    public void importFiles(File[] files, boolean recursive) {
        if (files != null) {
            DicomModel.loadingExecutor.execute(new LoadLocalDicom(files, recursive, model));
        }
    }

    @Override
    public List<Action> getOpenExportDialogAction() {
        ArrayList<Action> actions = new ArrayList<Action>(1);
        actions.add(exportAction);
        return actions;
    }

    @Override
    public List<Action> getOpenImportDialogAction() {
        ArrayList<Action> actions = new ArrayList<Action>(2);
        actions.add(importAction);
        AbstractAction importCDAction =
            new AbstractAction("DICOM CD", new ImageIcon(DicomExplorer.class.getResource("/icon/16x16/cd.png"))) { //$NON-NLS-1$ //$NON-NLS-2$

                @Override
                public void actionPerformed(ActionEvent e) {
                    File file = DicomDirImport.getDcmDirFromMedia();
                    if (file == null) {
                        int response =
                            JOptionPane
                                .showConfirmDialog(
                                    SwingUtilities.getWindowAncestor(DicomExplorer.this),
                                    "Cannot find DICOMDIR on media device, do you want to import manually?", (String) this.getValue(Action.NAME),//$NON-NLS-1$ 
                                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                        if (response == 0) {
                            ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(DicomExplorer.this);
                            DicomImport dialog =
                                new DicomImport(SwingUtilities.getWindowAncestor(DicomExplorer.this), model);
                            dialog.showPage(Messages.getString("DicomDirImport.dicomdir")); //$NON-NLS-1$
                            ColorLayerUI.showCenterScreen(dialog, layer);
                        }
                    } else {
                        DicomDirImport.loadDicomDir(file, model, true);
                    }

                }
            };
        actions.add(importCDAction);
        return actions;
    }

    @Override
    public boolean canImportFiles() {
        return true;
    }

}
