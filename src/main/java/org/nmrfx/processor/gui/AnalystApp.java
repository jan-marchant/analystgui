/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakList;
import de.codecentric.centerdevice.MenuToolkit;
import de.codecentric.centerdevice.dialogs.about.AboutStageBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.python.util.InteractiveInterpreter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.dialog.ExceptionDialog;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ToolBar;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.io.PeakReader;
import org.nmrfx.processor.gui.controls.FractionCanvas;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.project.GUIStructureProject;
import org.nmrfx.processor.utilities.WebConnect;
import org.nmrfx.project.Project;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.constraints.RDCConstraintSet;
import org.nmrfx.structure.chemistry.io.MoleculeIOException;
import org.nmrfx.structure.chemistry.io.NMRStarReader;
import org.nmrfx.structure.chemistry.io.NMRStarWriter;
import org.nmrfx.structure.chemistry.io.PDBFile;
import org.nmrfx.structure.chemistry.io.SDFile;
import org.nmrfx.structure.chemistry.io.Sequence;
import org.nmrfx.structure.chemistry.mol3D.MolSceneController;
import static javafx.application.Application.launch;
import javafx.scene.layout.VBox;
import org.nmrfx.processor.datasets.peaks.PeakLabeller;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.WindowIO;
import org.nmrfx.structure.chemistry.constraints.NoeSet;
import org.nmrfx.utils.GUIUtils;
import org.python.util.PythonInterpreter;

public class AnalystApp extends MainApp {

    private static String version = null;
    static String appName = "NMRFx Analyst";
    MenuToolkit menuTk;
    private static MenuBar mainMenuBar = null;
    Boolean isMac = null;

    static AnalystApp analystApp = null;

    public static MultipletController multipletController;
    public static RegionController regionController;
    public static AtomController atomController;
    public static LigandScannerController scannerController;
    public static MolSceneController molController;
    public static AtomBrowser atomBrowser;
    public static RNAPeakGeneratorSceneController rnaPeakGenController;
    public static PeakTableController peakTableController;
    public static NOETableController noeTableController;
    public static WindowIO windowIO = null;
    PeakAtomPicker peakAtomPicker = null;
    CheckMenuItem assignOnPick;
    RDCGUI rdcGUI = null;

    public static void closeAll() {
        Stage mainStage = getMainStage();
        for (Stage stage : stages) {
            if (stage != mainStage) {
                stage.close();
            }
        }
    }

    public void waitForCommit() {
        int nTries = 30;
        int iTry = 0;
        while (GUIStructureProject.isCommitting() && (iTry < nTries)) {
            System.out.println("committing");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                break;
            }
            iTry++;
        }

    }

    @Override
    public void start(Stage stage) throws Exception {
        MainApp.setAnalyst();
        mainApp = this;
        analystApp = this;
        FXMLController controller = FXMLController.create(stage);
        Platform.setImplicitExit(true);
        hostServices = getHostServices();
        stage.setTitle(appName + " " + getVersion());

        if (mainMenuBar == null) {
            mainMenuBar = makeMenuBar(appName);
        }
        ScannerController.addCreateAction(e -> updateScannerGUI(e));
        Parameters parameters = getParameters();
        System.out.println(parameters.getRaw());

        interpreter.exec("import os");
        interpreter.exec("from pyproc import *\ninitLocal()");
        interpreter.exec("from gscript import *\nnw=NMRFxWindowScripting()");
        interpreter.exec("from dscript import *");
        interpreter.exec("from mscript import *");
        interpreter.exec("from pscript import *");
        interpreter.set("argv", parameters.getRaw());
        interpreter.exec("parseArgs(argv)");
        Dataset.addObserver(this);
        PeakPicking.registerSinglePickAction((c) -> pickedPeakAction(c));
        PeakMenuBar.addExtra("Add Residue Prefix", PeakLabeller::labelWithSingleResidueChar);
        PeakMenuBar.addExtra("Remove Residue Prefix", PeakLabeller::removeSingleResidueChar);
        KeyBindings.registerGlobalKeyAction("pa", this::assignPeak);
    }

    private void updateScannerGUI(ScannerController scannerController) {
        System.out.println("update scanner " + scannerController);
        MinerController minerController = new MinerController(scannerController);
    }

    Object pickedPeakAction(Object peakObject) {
        if (assignOnPick.isSelected()) {
            Peak peak = (Peak) peakObject;
            System.out.println(peak.getName());
            PolyChart chart = FXMLController.getActiveController().getActiveChart();
            double x = chart.getMouseX();
            double y = chart.getMouseY();
            Canvas canvas = chart.canvas;
            Point2D sXY = canvas.localToScreen(x, y);
            if (peakAtomPicker == null) {
                peakAtomPicker = new PeakAtomPicker();
                peakAtomPicker.create();
            }
            peakAtomPicker.show(sXY.getX(), sXY.getY(), peak);
        }
        return null;
    }

    public static boolean isMac() {
        return SystemUtils.IS_OS_MAC;
    }

    public static MenuBar getMenuBar() {
        return mainApp.makeMenuBar(appName);
    }

    public static AnalystApp getAnalystApp() {
        return analystApp;
    }

    public static PreferencesController getPreferencesController() {
        return preferencesController;
    }

    public void quit() {
        System.out.println("quit");
        waitForCommit();
        Platform.exit();
        System.exit(0);
    }

    Stage makeAbout(String appName) {
        AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About " + appName)
                .withAppName(appName).withCloseOnFocusLoss().withHtml("<i>Processing for NMR Data</i>")
                .withVersionString("Version " + getVersion()).withCopyright("Copyright \u00A9 " + Calendar
                .getInstance().get(Calendar.YEAR));
        Image image = new Image(AnalystApp.class.getResourceAsStream("/images/Icon_NVFX_256.png"));
        aboutStageBuilder = aboutStageBuilder.withImage(image);
        return aboutStageBuilder.build();
    }

    MenuBar makeMenuBar(String appName) {
        MenuToolkit tk = null;
        if (isMac()) {
            tk = MenuToolkit.toolkit();
        }
        MenuBar menuBar = new MenuBar();

        // Application Menu
        // TBD: services menu
        Menu appMenu = new Menu(appName); // Name for appMenu can't be set at
        // Runtime
        MenuItem aboutItem = null;
        Stage aboutStage = makeAbout(appName);
        if (tk != null) {
            aboutItem = tk.createAboutMenuItem(appName, aboutStage);
        } else {
            aboutItem = new MenuItem("About...");
            aboutItem.setOnAction(e -> aboutStage.show());
        }
        MenuItem prefsItem = new MenuItem("Preferences...");
        MenuItem quitItem;
        prefsItem.setOnAction(e -> showPreferences(e));
        if (tk != null) {
            quitItem = tk.createQuitMenuItem(appName);
            quitItem.setOnAction(e -> quit());
            appMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), prefsItem, new SeparatorMenuItem(),
                    tk.createHideMenuItem(appName), tk.createHideOthersMenuItem(), tk.createUnhideAllMenuItem(),
                    new SeparatorMenuItem(), quitItem);
        } else {
            quitItem = new MenuItem("Quit");
            quitItem.setOnAction(e -> quit());
        }
        // File Menu (items TBD)
        Menu fileMenu = new Menu("File");
        MenuItem openMenuItem = new MenuItem("Open FID...");
        openMenuItem.setOnAction(e -> FXMLController.getActiveController().openFIDAction(e));
        MenuItem openDatasetMenuItem = new MenuItem("Open Dataset...");
        openDatasetMenuItem.setOnAction(e -> FXMLController.getActiveController().openDatasetAction(e));
        MenuItem addMenuItem = new MenuItem("Open Dataset (No Display) ...");
        addMenuItem.setOnAction(e -> FXMLController.getActiveController().addNoDrawAction(e));
        MenuItem newMenuItem = new MenuItem("New Window...");
        newMenuItem.setOnAction(e -> newGraphics(e));
        Menu recentFIDMenuItem = new Menu("Recent FIDs");
        Menu recentDatasetMenuItem = new Menu("Recent Datasets");
        PreferencesController.setupRecentMenus(recentFIDMenuItem, recentDatasetMenuItem);

        MenuItem pdfMenuItem = new MenuItem("Export PDF...");
        pdfMenuItem.setOnAction(e -> FXMLController.getActiveController().exportPDFAction(e));
        MenuItem svgMenuItem = new MenuItem("Export SVG...");
        svgMenuItem.setOnAction(e -> FXMLController.getActiveController().exportSVGAction(e));
        MenuItem loadPeakListMenuItem = new MenuItem("Load PeakLists");
        loadPeakListMenuItem.setOnAction(e -> loadPeakLists());
        MenuItem portMenuItem = new MenuItem("New NMRFx Server...");
        portMenuItem.setOnAction(e -> startServer(e));

        Menu projectMenu = new Menu("Projects");

        MenuItem projectOpenMenuItem = new MenuItem("Open...");
        projectOpenMenuItem.setOnAction(e -> loadProject());

        MenuItem projectSaveAsMenuItem = new MenuItem("Save As...");
        projectSaveAsMenuItem.setOnAction(e -> saveProjectAs());

        MenuItem projectSaveMenuItem = new MenuItem("Save");
        projectSaveMenuItem.setOnAction(e -> saveProject());
        Menu recentProjectMenuItem = new Menu("Open Recent");

        MenuItem closeProjectMenuItem = new MenuItem("Close");
        closeProjectMenuItem.setOnAction(e -> closeProject());

        MenuItem openSTARMenuItem = new MenuItem("Open STAR3...");
        openSTARMenuItem.setOnAction(e -> readSTAR());

        MenuItem saveSTARMenuItem = new MenuItem("Save STAR3...");
        saveSTARMenuItem.setOnAction(e -> writeSTAR());

        MenuItem openSparkyMenuItem = new MenuItem("Open Sparky Project...");
        openSparkyMenuItem.setOnAction(e -> readSparkyProject());

        List<Path> recentProjects = PreferencesController.getRecentProjects();
        for (Path path : recentProjects) {
            int count = path.getNameCount();
            int first = count - 3;
            first = first >= 0 ? first : 0;
            Path subPath = path.subpath(first, count);

            MenuItem projectMenuItem = new MenuItem(subPath.toString());
            projectMenuItem.setOnAction(e -> loadProject(path));
            recentProjectMenuItem.getItems().add(projectMenuItem);
        }

        projectMenu.getItems().addAll(projectOpenMenuItem, recentProjectMenuItem,
                projectSaveMenuItem, projectSaveAsMenuItem, closeProjectMenuItem,
                openSTARMenuItem, saveSTARMenuItem, openSparkyMenuItem);

        fileMenu.getItems().addAll(openMenuItem, openDatasetMenuItem, addMenuItem,
                recentFIDMenuItem, recentDatasetMenuItem, newMenuItem, portMenuItem, new SeparatorMenuItem(), svgMenuItem, loadPeakListMenuItem);

        Menu spectraMenu = new Menu("Spectra");
        MenuItem deleteItem = new MenuItem("Delete Spectrum");
        deleteItem.setOnAction(e -> FXMLController.getActiveController().getActiveChart().close());
        MenuItem syncMenuItem = new MenuItem("Sync Axes");
        syncMenuItem.setOnAction(e -> PolyChart.activeChart.get().syncSceneMates());

        Menu arrangeMenu = new Menu("Arrange");
        MenuItem horizItem = new MenuItem("Horizontal");
        horizItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.HORIZONTAL));
        MenuItem vertItem = new MenuItem("Vertical");
        vertItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.VERTICAL));
        MenuItem gridItem = new MenuItem("Grid");
        gridItem.setOnAction(e -> FXMLController.getActiveController().arrange(FractionCanvas.ORIENTATION.GRID));
        MenuItem overlayItem = new MenuItem("Overlay");
        overlayItem.setOnAction(e -> FXMLController.getActiveController().overlay());
        MenuItem minimizeItem = new MenuItem("Minimize Borders");
        minimizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(true));
        MenuItem normalizeItem = new MenuItem("Normal Borders");
        normalizeItem.setOnAction(e -> FXMLController.getActiveController().setBorderState(false));

        arrangeMenu.getItems().addAll(horizItem, vertItem, gridItem, overlayItem, minimizeItem, normalizeItem);
        MenuItem alignMenuItem = new MenuItem("Align Spectra");
        alignMenuItem.setOnAction(e -> FXMLController.getActiveController().alignCenters());
        MenuItem analyzeMenuItem = new MenuItem("Analyzer...");
        analyzeMenuItem.setOnAction(e -> showAnalyzer(e));
        MenuItem measureMenuItem = new MenuItem("Show Measure Bar");
        measureMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpectrumMeasureBar());
        MenuItem compareMenuItem = new MenuItem("Show Comparator");
        compareMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpectrumComparator());
        MenuItem stripsMenuItem = new MenuItem("Show Strips");
        stripsMenuItem.setOnAction(e -> showStripsBar());
        MenuItem favoritesMenuItem = new MenuItem("Favorites");
        favoritesMenuItem.setOnAction(e -> showFavorites());
        MenuItem copyItem = new MenuItem("Copy Spectrum as SVG Text");
        copyItem.setOnAction(e -> FXMLController.getActiveController().copySVGAction(e));
        spectraMenu.getItems().addAll(deleteItem, arrangeMenu, favoritesMenuItem, syncMenuItem,
                alignMenuItem, analyzeMenuItem, measureMenuItem, compareMenuItem,
                stripsMenuItem, copyItem);

        // Format (items TBD)
//        Menu formatMenu = new Menu("Format");
//        formatMenu.getItems().addAll(new MenuItem("TBD"));
        // View Menu (items TBD)
        Menu molMenu = new Menu("Molecules");
        Menu molFileMenu = new Menu("File");

        MenuItem readSeqItem = new MenuItem("Read Sequence...");
        readSeqItem.setOnAction(e -> readMolecule("seq"));
        molFileMenu.getItems().add(readSeqItem);
        MenuItem readPDBItem = new MenuItem("Read PDB...");
        readPDBItem.setOnAction(e -> readMolecule("pdb"));
        molFileMenu.getItems().add(readPDBItem);
        MenuItem readCoordinatesItem = new MenuItem("Read Coordinates ...");
        readCoordinatesItem.setOnAction(e -> readMolecule("pdb xyz"));
        molFileMenu.getItems().add(readCoordinatesItem);
        MenuItem readPDBxyzItem = new MenuItem("Read PDB XYZ...");
        readPDBxyzItem.setOnAction(e -> readMolecule("pdbx"));
        molFileMenu.getItems().add(readPDBxyzItem);
        MenuItem readMolItem = new MenuItem("Read Mol...");
        readMolItem.setOnAction(e -> readMolecule("mol"));
        molFileMenu.getItems().add(readMolItem);
        MenuItem seqGUIMenuItem = new MenuItem("Sequence GUI");
        seqGUIMenuItem.setOnAction(e -> SequenceGUI.showGUI(this));

        MenuItem atomsMenuItem = new MenuItem("Atoms");
        atomsMenuItem.setOnAction(e -> showAtoms(e));

        MenuItem molMenuItem = new MenuItem("Viewer");
        molMenuItem.setOnAction(e -> showMols());

        MenuItem rdcMenuItem = new MenuItem("RDC Analysis...");
        rdcMenuItem.setOnAction(e -> showRDCGUI());

        molMenu.getItems().addAll(molFileMenu, seqGUIMenuItem, atomsMenuItem, molMenuItem, rdcMenuItem);

        Menu viewMenu = new Menu("View");
        MenuItem dataMenuItem = new MenuItem("Show Datasets");
        dataMenuItem.setOnAction(e -> showDatasetsTable(e));

        MenuItem consoleMenuItem = new MenuItem("Show Console");
        consoleMenuItem.setOnAction(e -> showConsole(e));

        MenuItem attrMenuItem = new MenuItem("Show Attributes");
        attrMenuItem.setOnAction(e -> FXMLController.getActiveController().showSpecAttrAction(e));

        MenuItem procMenuItem = new MenuItem("Show Processor");
        procMenuItem.setOnAction(e -> FXMLController.getActiveController().showProcessorAction(e));

        MenuItem scannerMenuItem = new MenuItem("Show Scanner");
        scannerMenuItem.setOnAction(e -> FXMLController.getActiveController().showScannerAction(e));

        MenuItem rnaPeakGenMenuItem = new MenuItem("Show RNA Label Scheme");
        rnaPeakGenMenuItem.setOnAction(e -> showRNAPeakGenerator(e));

        viewMenu.getItems().addAll(consoleMenuItem, dataMenuItem, attrMenuItem, procMenuItem, scannerMenuItem, rnaPeakGenMenuItem);

        Menu peakMenu = new Menu("Peaks");

        MenuItem peakAttrMenuItem = new MenuItem("Show Peak Tool");
        peakAttrMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakAttrAction(e));

        MenuItem peakNavigatorMenuItem = new MenuItem("Show Peak Navigator");
        peakNavigatorMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakNavigator());

        MenuItem peakTableMenuItem = new MenuItem("Show Peak Table");
        peakTableMenuItem.setOnAction(e -> showPeakTable());

        MenuItem linkPeakDimsMenuItem = new MenuItem("Link by Labels");
        linkPeakDimsMenuItem.setOnAction(e -> FXMLController.getActiveController().linkPeakDims());

        MenuItem peakSliderMenuItem = new MenuItem("Show Peak Slider");
        peakSliderMenuItem.setOnAction(e -> FXMLController.getActiveController().showPeakSlider());

        MenuItem pathToolMenuItem = new MenuItem("Show Path Tool");
        pathToolMenuItem.setOnAction(e -> FXMLController.getActiveController().showPathTool());

        MenuItem ligandScannerMenuItem = new MenuItem("Show Ligand Scanner");
        ligandScannerMenuItem.setOnAction(e -> showLigandScanner(e));

        MenuItem noeTableMenuItem = new MenuItem("Show NOE Table");
        noeTableMenuItem.setOnAction(e -> showNOETable());

        Menu assignCascade = new Menu("Assign Tools");

        assignOnPick = new CheckMenuItem("Assign on Pick");

        MenuItem peakAssignerItem = new MenuItem("Show Peak Assigner");
        peakAssignerItem.setOnAction(e -> assignPeak());

        MenuItem atomBrowserMenuItem = new MenuItem("Show Atom Browser");
        atomBrowserMenuItem.setOnAction(e -> showAtomBrowser());

        MenuItem runAboutMenuItem = new MenuItem("Show RunAboutX");
        runAboutMenuItem.setOnAction(e -> showRunAbout());

        assignCascade.getItems().addAll(peakAssignerItem, assignOnPick,
                atomBrowserMenuItem, runAboutMenuItem);

        peakMenu.getItems().addAll(peakAttrMenuItem, peakNavigatorMenuItem,
                peakTableMenuItem, linkPeakDimsMenuItem, peakSliderMenuItem,
                pathToolMenuItem, ligandScannerMenuItem,
                noeTableMenuItem,
                assignCascade);

        Menu oneDMenu = new Menu("Analysis (1D)");

        MenuItem regionsMenuItem = new MenuItem("Show Regions Analyzer");
        regionsMenuItem.setOnAction(e -> showRegionAnalyzer(e));

        MenuItem multipletMenuItem = new MenuItem("Show Multiplet Analyzer");
        multipletMenuItem.setOnAction(e -> showMultipletAnalyzer(e));

        MenuItem spectrumLibraryMenuItem = new MenuItem("Show Spectrum Library");
        spectrumLibraryMenuItem.setOnAction(e -> showSpectrumLibrary());

        MenuItem spectrumFitLibraryMenuItem = new MenuItem("Show Spectrum Fitter");
        spectrumFitLibraryMenuItem.setOnAction(e -> showSpectrumFitter());

        oneDMenu.getItems().addAll(regionsMenuItem, multipletMenuItem,
                spectrumLibraryMenuItem, spectrumFitLibraryMenuItem);

        // Window Menu
        // TBD standard window menu items
        // Help Menu (items TBD)
        Menu helpMenu = new Menu("Help");

        MenuItem webSiteMenuItem = new MenuItem("NMRFx Web Site");
        webSiteMenuItem.setOnAction(e -> showWebSiteAction(e));

        MenuItem docsMenuItem = new MenuItem("Online Documentation");
        docsMenuItem.setOnAction(e -> showDocAction(e));

        MenuItem versionMenuItem = new MenuItem("Check Version");
        versionMenuItem.setOnAction(e -> showVersionAction(e));

        MenuItem mailingListItem = new MenuItem("Mailing List Site");
        mailingListItem.setOnAction(e -> showMailingListAction(e));

        MenuItem refMenuItem = new MenuItem("NMRFx Publication");
        refMenuItem.setOnAction(e -> {
            AnalystApp.hostServices.showDocument("http://link.springer.com/article/10.1007/s10858-016-0049-6");
        });

        // home
        // mailing list
        //
        helpMenu.getItems().addAll(docsMenuItem, webSiteMenuItem, mailingListItem, versionMenuItem, refMenuItem);

        if (tk != null) {
            Menu windowMenu = new Menu("Window");
            windowMenu.getItems().addAll(tk.createMinimizeMenuItem(), tk.createZoomMenuItem(), tk.createCycleWindowsItem(),
                    new SeparatorMenuItem(), tk.createBringAllToFrontItem());
            menuBar.getMenus().addAll(appMenu, fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, oneDMenu, peakMenu, windowMenu, helpMenu);
            tk.autoAddWindowMenuItems(windowMenu);
            tk.setGlobalMenuBar(menuBar);
        } else {
            fileMenu.getItems().add(prefsItem);
            fileMenu.getItems().add(quitItem);
            menuBar.getMenus().addAll(fileMenu, projectMenu, spectraMenu, molMenu, viewMenu, oneDMenu, peakMenu, helpMenu);
            helpMenu.getItems().add(0, aboutItem);
        }
        return menuBar;
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    public static String getVersion() {
        if (version == null) {
            String cp = System.getProperty("java.class.path");
            // processorgui-10.1.2.jar
            String jarPattern = ".*processorgui-([0-9\\.\\-abcr]+)\\.jar.*";
            Pattern pat = Pattern.compile(jarPattern);
            Matcher match = pat.matcher(cp);
            version = "0.0.0";
            if (match.matches()) {
                version = match.group(1);
            }
        }
        return version;
    }

    static void showDocAction(ActionEvent event) {
        hostServices.showDocument("http://docs.nmrfx.org");
    }

    static void showWebSiteAction(ActionEvent event) {
        hostServices.showDocument("http://nmrfx.org");
    }

    static void showMailingListAction(ActionEvent event) {
        hostServices.showDocument("https://groups.google.com/forum/#!forum/nmrfx-processor");
    }

    public void showVersionAction(ActionEvent event) {
        String onlineVersion = WebConnect.getVersion();
        onlineVersion = onlineVersion.replace('_', '.');
        String currentVersion = getVersion();
        String text;
        if (onlineVersion.equals("")) {
            text = "Sorry, couldn't reach web site";
        } else if (onlineVersion.equals(currentVersion)) {
            text = "You're running the latest version: " + currentVersion;
        } else {
            text = "You're running " + currentVersion;
            text += "\nbut the latest is: " + onlineVersion;
        }
        Alert alert = new Alert(AlertType.INFORMATION, text);
        alert.setTitle("NMRFx Analyst Version");
        alert.showAndWait();
    }

    private void showConsole(ActionEvent event) {
        AnalystApp.getConsoleController().show();
    }

    @FXML
    private void showPreferences(ActionEvent event) {
        if (preferencesController == null) {
            preferencesController = PreferencesController.create(stages.get(0));
            addPrefs();
        }
        if (preferencesController != null) {
            preferencesController.getStage().show();
        } else {
            System.out.println("Coudn't make controller");
        }
    }

    private void newGraphics(ActionEvent event) {
        FXMLController controller = FXMLController.create();
    }

    @FXML
    void showDatasetsTable(ActionEvent event) {
        if (datasetController == null) {
            datasetController = DatasetsController.create();
            datasetController.setDatasetList(FXMLController.datasetList);
        }
        datasetController.getStage().show();
        datasetController.getStage().toFront();
    }

    void loadPeakLists() {
        PeakReader peakReader = new PeakReader();
        Dataset.datasets().stream().forEach(dataset -> {
            String canonFileName = dataset.getCanonicalFile();
            File canonFile = new File(canonFileName);
            if (canonFile.exists()) {
                int dotIndex = canonFileName.lastIndexOf(".");
                if (dotIndex != -1) {
                    String listFileName = canonFileName.substring(0, dotIndex) + ".xpk2";
                    File listFile = new File(listFileName);
                    String listName = listFile.getName();
                    dotIndex = listName.lastIndexOf('.');
                    listName = listName.substring(0, dotIndex);
                    if (PeakList.get(listName) == null) {
                        try {
                            peakReader.readXPK2Peaks(listFileName);
                        } catch (IOException ioE) {
                            ExceptionDialog dialog = new ExceptionDialog(ioE);
                            dialog.showAndWait();
                        }
                    }
                }
            }
        });
    }

    public static InteractiveInterpreter getInterpreter() {
        return interpreter;
    }

    public static ConsoleController getConsoleController() {
        return consoleController;
    }

    public static void setConsoleController(ConsoleController controller) {
        consoleController = controller;
    }

    public static void writeOutput(String string) {
        if (consoleController == null) {
            System.out.println(string);
        } else {
            consoleController.writeOutput(string);
        }
    }

    @FXML
    private void showRNAPeakGenerator(ActionEvent event) {
        if (rnaPeakGenController == null) {
            rnaPeakGenController = RNAPeakGeneratorSceneController.create();
        }
        if (rnaPeakGenController != null) {
            rnaPeakGenController.getStage().show();
            rnaPeakGenController.getStage().toFront();
        } else {
            System.out.println("Coudn't make rnaPeakGenController ");
        }
    }

    @FXML
    private void showAtoms(ActionEvent event) {
        if (atomController == null) {
            atomController = AtomController.create();
        }
        if (atomController != null) {
            atomController.getStage().show();
            atomController.getStage().toFront();
        } else {
            System.out.println("Couldn't make atom controller");
        }
    }

    @FXML
    private void showMols() {
        if (molController == null) {
            molController = MolSceneController.create();
        }
        if (molController != null) {
            molController.getStage().show();
            molController.getStage().toFront();
        } else {
            System.out.println("Coudn't make molController");
        }
    }

    @FXML
    private void showLigandScanner(ActionEvent event) {
        if (scannerController == null) {
            scannerController = LigandScannerController.create();
        }
        if (scannerController != null) {
            scannerController.getStage().show();
            scannerController.getStage().toFront();
        } else {
            System.out.println("Coudn't make atom controller");
        }
    }

    private void showPeakTable() {
        if (peakTableController == null) {
            peakTableController = PeakTableController.create();
            List<PeakList> peakLists = PeakList.getLists();
            if (!peakLists.isEmpty()) {
                peakTableController.setPeakList(peakLists.get(0));
            }
        }
        if (peakTableController != null) {
            peakTableController.getStage().show();
            peakTableController.getStage().toFront();
        } else {
            System.out.println("Coudn't make peak table controller");
        }
    }

    private void showNOETable() {
        if (noeTableController == null) {
            noeTableController = NOETableController.create();
            List<NoeSet> noeSets = NoeSet.getSets();
            if (!noeSets.isEmpty()) {
                noeTableController.setNoeSet(noeSets.get(0));
            }
        }
        if (noeTableController != null) {
            noeTableController.getStage().show();
            noeTableController.getStage().toFront();
            noeTableController.updateNoeSetMenu();
        } else {
            System.out.println("Coudn't make NOE table controller");
        }
    }

    public static Project getActive() {
        return GUIStructureProject.getActive();
    }

    private void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Project Chooser");
        File directoryFile = chooser.showDialog(null);
        if (directoryFile != null) {
            loadProject(directoryFile.toPath());
        }
    }

    private void loadProject(Path path) {
        if (path != null) {
            String projectName = path.getFileName().toString();
            GUIStructureProject project = new GUIStructureProject(projectName);
            try {
                project.loadGUIProject(path);
            } catch (IOException | MoleculeIOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }

    }

    private void saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Project Creator");
        File directoryFile = chooser.showSaveDialog(null);
        if (directoryFile != null) {
            GUIStructureProject activeProject = (GUIStructureProject) getActive();
            if (activeProject != null) {
                GUIStructureProject newProject = GUIStructureProject.replace(appName, activeProject);

                try {
                    newProject.createProject(directoryFile.toPath());
                    newProject.saveProject();
                } catch (IOException ex) {
                    ExceptionDialog dialog = new ExceptionDialog(ex);
                    dialog.showAndWait();
                }
            }
        }

    }

    private void saveProject() {
        GUIStructureProject project = (GUIStructureProject) getActive();
        if (project.hasDirectory()) {
            try {
                project.saveProject();
            } catch (IOException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
            }
        }
    }

    @Override
    public void datasetAdded(Dataset dataset) {
        if (Platform.isFxApplicationThread()) {
            FXMLController.updateDatasetList();
        } else {
            Platform.runLater(() -> {
                FXMLController.updateDatasetList();
            }
            );
        }
    }

    @Override
    public void datasetModified(Dataset dataset) {
    }

    @Override
    public void datasetRemoved(Dataset dataset) {
        if (Platform.isFxApplicationThread()) {
            FXMLController.updateDatasetList();
        } else {
            Platform.runLater(() -> {
                FXMLController.updateDatasetList();
            }
            );
        }
    }

    @Override
    public void datasetRenamed(Dataset dataset) {
        if (Platform.isFxApplicationThread()) {
            FXMLController.updateDatasetList();
        } else {
            Platform.runLater(() -> {
                FXMLController.updateDatasetList();
            }
            );
        }
    }

    public void showAtomBrowser() {
        if (atomBrowser == null) {
            ToolBar navBar = new ToolBar();
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().add(navBar);
            atomBrowser = new AtomBrowser(controller, this::removeAtomBrowser);
            atomBrowser.initSlider(navBar);
        }
    }

    public void removeAtomBrowser(Object o) {
        if (atomBrowser != null) {
            FXMLController controller = FXMLController.getActiveController();
            controller.getBottomBox().getChildren().remove(atomBrowser.getToolBar());
            atomBrowser = null;
        }
    }

    public void assignPeak(String keyStr, PolyChart chart) {
        assignPeak();
    }

    public void assignPeak() {
        if (peakAtomPicker == null) {
            peakAtomPicker = new PeakAtomPicker();
            peakAtomPicker.create();
        }
        peakAtomPicker.show(300, 300, null);

    }

    @FXML
    private void showAnalyzer(ActionEvent event) {
        if (analyzerController == null) {
            analyzerController = new AnalyzerController();
        }
        try {
            analyzerController.load();
        } catch (IOException ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
        }
    }

    @FXML
    private void showMultipletAnalyzer(ActionEvent event) {
        if (multipletController == null) {
            multipletController = MultipletController.create();
        } else {
            multipletController.initMultiplet();
        }
        multipletController.getStage().show();
        multipletController.getStage().toFront();
    }

    @FXML
    private void showRegionAnalyzer(ActionEvent event) {
        if (regionController == null) {
            regionController = regionController.create();
        } else {
            regionController.initMultiplet();
        }
        regionController.getStage().show();
        regionController.getStage().toFront();
    }

    void closeProject() {
        if (GUIUtils.affirm("Close all project information")) {
            ((GUIStructureProject) getActive()).close();
        }
    }

    @FXML
    void readSTAR() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read STAR3 File");
        File starFile = chooser.showOpenDialog(null);
        if (starFile != null) {
            try {
                NMRStarReader.read(starFile);
                if (rdcGUI != null) {
                    rdcGUI.bmrbFile.setText(starFile.getName());
                    rdcGUI.setChoice.getItems().clear();
                    if (!RDCConstraintSet.getNames().isEmpty()) {
                        rdcGUI.setChoice.getItems().addAll(RDCConstraintSet.getNames());
                        rdcGUI.setChoice.setValue(rdcGUI.setChoice.getItems().get(0));
                    }

                }
            } catch (ParseException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
                return;
            }
        }
    }

    void writeSTAR() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Write STAR3 File");
        File starFile = chooser.showSaveDialog(null);
        if (starFile != null) {
            try {
                NMRStarWriter.writeAll(starFile);
            } catch (IOException | ParseException | InvalidPeakException | InvalidMoleculeException ex) {
                ExceptionDialog dialog = new ExceptionDialog(ex);
                dialog.showAndWait();
                return;
            }
        }
    }

    void readMolecule(String type) {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                switch (type) {
                    case "pdb": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.readSequence(file.toString(), false);
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdbx": {
                        PDBFile pdbReader = new PDBFile();
                        pdbReader.read(file.toString());
                        System.out.println("read mol: " + file.toString());
                        break;
                    }
                    case "pdb xyz":
                        PDBFile pdb = new PDBFile();
                        pdb.readCoordinates(file.getPath(), 0, false, true);
                        Molecule mol = Molecule.getActive();
                        mol.updateAtomArray();
                        System.out.println("read mol: " + file.toString());
                        if (rdcGUI != null) {
                            rdcGUI.pdbFile.setText(file.getName());
                        }
                        break;
                    case "sdf":
                    case "mol":
                        SDFile.read(file.toString(), null);
                        break;
                    case "seq":
                        Sequence seq = new Sequence();
                        seq.read(file.toString());
                        break;
                    default:
                        break;
                }
                showMols();
            } catch (MoleculeIOException ioE) {
                ExceptionDialog dialog = new ExceptionDialog(ioE);
                dialog.showAndWait();
            }

            if (atomController != null) {
                atomController.setFilterString("");
            }
        }
    }

    void readSparkyProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Read Sparky Project");
        File sparkyFile = chooser.showOpenDialog(null);
        Map<String, Object> pMap = null;
        if (sparkyFile != null) {
            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.exec("import sparky");
            String rdString;
            interpreter.set("pMap", pMap);
            interpreter.exec("sparky.pMap=pMap");
            rdString = String.format("sparky.loadProjectFile('%s')", sparkyFile.toString());
            interpreter.exec(rdString);
        }
    }

    void showRDCGUI() {
        if (rdcGUI == null) {
            rdcGUI = new RDCGUI(this);
        }
        rdcGUI.showRDCplot();
    }

    public void showSpectrumLibrary() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(SimMolController.class)) {
            ToolBar navBar = new ToolBar();
            controller.getBottomBox().getChildren().add(navBar);
            SimMolController simMol = new SimMolController(controller, this::removeMolSim);
            simMol.initialize(navBar);
            controller.addTool(simMol);
        }
    }

    public void removeMolSim(SimMolController simMolController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(SimMolController.class);
        controller.getBottomBox().getChildren().remove(simMolController.getToolBar());
    }

    public void showSpectrumFitter() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(SimFitMolController.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            ToolBar navBar = new ToolBar();
            ToolBar fitBar = new ToolBar();
            vBox.getChildren().add(navBar);
            vBox.getChildren().add(fitBar);
            SimFitMolController simFit = new SimFitMolController(controller, this::removeMolFitter);
            simFit.initialize(vBox, navBar, fitBar);
            controller.addTool(simFit);
        }
    }

    public void removeMolFitter(SimFitMolController simMolController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(SimFitMolController.class);
        controller.getBottomBox().getChildren().remove(simMolController.getBox());
    }

    public void showStripsBar() {
        FXMLController controller = FXMLController.getActiveController();
        if (!controller.containsTool(StripController.class)) {
            VBox vBox = new VBox();
            controller.getBottomBox().getChildren().add(vBox);
            StripController stripsController = new StripController(controller, this::removeStripsBar);
            stripsController.initialize(vBox);
            controller.addTool(stripsController);
        }
    }

    public StripController getStripsTool() {
        FXMLController controller = FXMLController.getActiveController();
        StripController stripsController = (StripController) controller.getTool(StripController.class);
        return stripsController;
    }

    public void removeStripsBar(StripController stripsController) {
        FXMLController controller = FXMLController.getActiveController();
        controller.removeTool(StripController.class);
        controller.getBottomBox().getChildren().remove(stripsController.getBox());
    }

    void addPrefs() {
        AnalystPrefs.addPrefs();
    }

    void showFavorites() {
        if (windowIO == null) {
            windowIO = new WindowIO();
            windowIO.create();
        }
        Stage stage = windowIO.getStage();
        stage.show();
        stage.toFront();
        windowIO.updateFavorites();
        try {
            Project project = Project.getActive();
            if (project != null) {
                Path projectDir = project.getDirectory();
                Path path = projectDir.getFileSystem().getPath(projectDir.toString(), "windows");
                windowIO.setupWatcher(path);
            }
        } catch (IOException ex) {
        }
    }

}
