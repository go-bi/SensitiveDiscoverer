/*
Copyright (C) 2021 CYS4 Srl
See the file 'LICENSE' for copying permission
*/
package com.cys4.sensitivediscoverer.ui;

import burp.IBurpExtenderCallbacks;
import burp.ITab;
import burp.ITextEditor;
import burp.SpringUtilities;
import com.cys4.sensitivediscoverer.controller.Utils;
import com.cys4.sensitivediscoverer.model.LogEntity;
import com.cys4.sensitivediscoverer.model.ProxyItemSection;
import com.cys4.sensitivediscoverer.model.RegexEntity;
import com.cys4.sensitivediscoverer.scanner.BurpLeaksScanner;
import com.cys4.sensitivediscoverer.seed.RegexSeeder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;

public class MainUI implements ITab {

    // default options
    private Properties _PROPERTIES_PluginProperties;

    // ui components
    private LogTableEntriesUI logTableEntriesUI;
    private LogTableEntryUI logTableEntryUI;
    private JSplitPane splitPane;
    private ITextEditor originalRequestViewer;
    private ITextEditor originalResponseViewer;
    private final IBurpExtenderCallbacks callbacks;

    /**
     * Analyze Proxy History
     */
    private Thread analyzeProxyHistoryThread;
    private boolean isAnalysisRunning;

    private final List<LogEntity> logEntries;
    private final List<RegexEntity> generalRegexList;
    private final List<RegexEntity> extensionsRegexList;

    private final BurpLeaksScanner burpLeaksScanner;

    /**
     * Checkbox to skip responses not in scope
     */
    private static boolean inScopeCheckbox = false;
    /**
     * Checkbox to skip responses over a set max size
     */
    private static boolean skipMaxSizeCheckbox = true;
    /**
     * Max response size in bytes. Defaults to 10MB
     */
    private static int maxSizeValue = 10_000_000;
    /**
     * Checkbox to skip responses of a media MIME-type
     */
    private static boolean skipMediaTypeCheckbox = true;

    public MainUI(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;

        this.generalRegexList = RegexSeeder.getGeneralRegexes();
        this.extensionsRegexList = RegexSeeder.getExtensionRegexes();

        // Analyze Proxy History
        this.analyzeProxyHistoryThread = null;
        this.isAnalysisRunning = false;

        // Logger elements
        this.logEntries = new ArrayList<>();
        this.burpLeaksScanner = new BurpLeaksScanner(4, this, callbacks, logEntries, this.generalRegexList, this.extensionsRegexList);

        LoadConfigFile();
    }

    private void LoadConfigFile() {
        // load the prop files
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            assert (input != null);

            //load a properties file from class path
            _PROPERTIES_PluginProperties = new Properties();
            _PROPERTIES_PluginProperties.load(input);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * GetNameExtension return the name of the extension from the configuration file
     */
    public String getNameExtension() {
        return _PROPERTIES_PluginProperties.getProperty("ui.name.extension_name");
    }

    /**
     * returns true if the option checkbox is selected
     */
    public static boolean isInScopeOptionSelected() {
        return inScopeCheckbox;
    }
    /**
     * returns true if the option checkbox is selected
     */
    public static boolean isSkipMaxSizeOptionSelected() {
        return skipMaxSizeCheckbox;
    }
    /**
     * Returns the set max size for responses
     */
    public static int getMaxSizeValueOption() {
        return maxSizeValue;
    }
    /**
     * returns true if the option checkbox is selected
     */
    public static boolean isSkipMediaTypeOptionSelected() {
        return skipMediaTypeCheckbox;
    }

    /**
     * used by the burp extender to add a new entry
     */
    public void logTableEntriesUIAddNewRow(int row) {
        logTableEntriesUI.addNewRow(row);
    }

    public JSplitPane getSplitPane() {
        return splitPane;
    }

    /**
     * Main function which initializes the extension and creates the UI
     */
    public void initialize() {
        // Updates the UI in async method
        SwingUtilities.invokeLater(this::_initialize);
    }

    private void _initialize() {
        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("Logger", createLoggerPanel());
        tabbedPane.addTab("Options", createOptionsPanel());
        tabbedPane.addTab("About", createAboutPanel());

        // main panel
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.add(tabbedPane);
        callbacks.customizeUiComponent(splitPane);

        // add the custom tab to Burp's UI
        callbacks.addSuiteTab(MainUI.this);
    }

    private JPanel createAboutPanel() {
        JPanel tabAbout = new JPanel();
        tabAbout.setLayout(new BoxLayout(tabAbout, BoxLayout.Y_AXIS));

        JLabel headerLabel = new JLabel(" SensitiveDiscoverer");
        headerLabel.setFont(new Font("Lucida Grande", Font.BOLD, 28)); // NOI18N

        JLabel subheaderLabel = new JLabel("  Scan for sensitive strings in HTTP messages");
        subheaderLabel.setFont(new Font("Lucida Grande", Font.ITALIC, 20)); // NOI18N

        tabAbout.add(new JLabel(" "));
        tabAbout.add(headerLabel);
        tabAbout.add(subheaderLabel);
        tabAbout.add(new JLabel(" "));
        tabAbout.add(new JLabel("  —————————————————————————————————————————————————"));
        tabAbout.add(new JLabel(" "));

        for (String s : Arrays.asList("Version: 3.0.0", "Author: CYS4",
                " ", " ",
                "Visit our site and our blog.", " ")) {
            JLabel label = new JLabel("  " + s);
            label.setFont(new Font("Lucida Grande", Font.PLAIN, 18));
            tabAbout.add(label);
        }

        JButton websiteButton = new JButton("CYS4 Website");
        websiteButton.setMaximumSize(new Dimension(400, 40));
        websiteButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://cys4.com"));
            } catch (IOException | URISyntaxException e) {}
        });
        tabAbout.add(websiteButton);
        JButton blogButton = new JButton("CYS4 Blog");
        blogButton.setMaximumSize(new Dimension(400, 40));
        blogButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://blog.cys4.com"));
            } catch (IOException | URISyntaxException e) {}
        });
        tabAbout.add(blogButton);

        for (String s : Arrays.asList(" ", " ", "We are open to requests and improvements. Visit our Github page!", " ")) {
            JLabel label = new JLabel("  " + s);
            label.setFont(new Font("Lucida Grande", Font.PLAIN, 18));
            tabAbout.add(label);
        }

        JButton githubButton = new JButton("Github page");
        githubButton.setMaximumSize(new Dimension(400, 40));
        githubButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/CYS4srl/CYS4-SensitiveDiscoverer"));
            } catch (IOException | URISyntaxException e) {}
        });
        tabAbout.add(githubButton);

        tabAbout.add(new JLabel(" "));

        // Logo
        try {
            BufferedImage logoImage = ImageIO.read(Objects.requireNonNull(MainUI.class.getClassLoader().getResource("logo.png")));
            JLabel logoIcon = new JLabel(new ImageIcon(logoImage.getScaledInstance(400, -1, Image.SCALE_DEFAULT)));
            tabAbout.add(logoIcon);
            tabAbout.add(new JLabel(" "));
        } catch (IOException ignored) {}

        callbacks.customizeUiComponent(tabAbout);

        return tabAbout;
    }

    private JPanel createLoggerPanel() {
        JPanel tabPanelLogger = new JPanel();
        tabPanelLogger.setLayout(new BorderLayout());

        JScrollPane scrollPaneLogger = setupLogger_LogTable();

        // panel that contains the buttonsPanel and the loggerPane
        JPanel buttonsLoggerPanel = createLogger_ButtonPanel(tabPanelLogger, scrollPaneLogger);

        // Request / Response viewers under the logger
        JSplitPane requestResponseSplitPane = createLogger_RequestResponse();

        // panel that contains all the graphical elements in the Logger Panel
        JSplitPane mainLoggerPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buttonsLoggerPanel, requestResponseSplitPane);
        tabPanelLogger.add(mainLoggerPanel);

        callbacks.customizeUiComponent(logTableEntryUI);
        callbacks.customizeUiComponent(mainLoggerPanel);
        callbacks.customizeUiComponent(scrollPaneLogger);
        callbacks.customizeUiComponent(requestResponseSplitPane);

        return tabPanelLogger;
    }

    /**
     * Panel that contains the buttonsPanel and the loggerPane
     * @param tabPanelLogger Panel where everything is rendered.
     * @param scrollPaneLogger Scroll pane where log entries are saved.
     */
    private JPanel createLogger_ButtonPanel(JPanel tabPanelLogger, JScrollPane scrollPaneLogger) {
        JPanel buttonPanelLog = new JPanel();
        buttonPanelLog.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        createLogger_AnalyzeHTTPHistory(tabPanelLogger)
            .forEach((component) -> buttonPanelLog.add(component, BorderLayout.NORTH));

        JButton clearLogsBtn = createLogger_ClearLogs(scrollPaneLogger);
        buttonPanelLog.add(clearLogsBtn, BorderLayout.NORTH);
        JMenuBar exportLogsMenu = createLogger_ExportLogs();
        buttonPanelLog.add(exportLogsMenu, BorderLayout.NORTH);

        JPanel buttonsLoggerPanel = new JPanel();
        buttonsLoggerPanel.setLayout(new BoxLayout(buttonsLoggerPanel, BoxLayout.Y_AXIS));
        buttonsLoggerPanel.setLayout(new BorderLayout());
        buttonsLoggerPanel.add(buttonPanelLog, BorderLayout.NORTH);
        buttonsLoggerPanel.add(scrollPaneLogger, BorderLayout.CENTER);

        return buttonsLoggerPanel;
    }

    private JScrollPane setupLogger_LogTable() {
        logTableEntriesUI = new LogTableEntriesUI(this.logEntries);
        originalRequestViewer = this.callbacks.createTextEditor();
        originalResponseViewer = this.callbacks.createTextEditor();
        logTableEntryUI = new LogTableEntryUI(logTableEntriesUI, this.logEntries, this.originalRequestViewer, this.originalResponseViewer);
        // disable sorting on columns while scanning. This helps to prevent Swing exceptions.
        logTableEntryUI.getTableHeader().putClientProperty("analysisDependent", "1");

        // when you right-click on a logTable entry, it will appear a context menu defined here
        MouseAdapter contextMenu = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMouseEvent(e);
            }

            private void onMouseEvent(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = logTableEntryUI.getSelectedRow();
                    if (row == -1) return;
                    logTableEntryUI.setRowSelectionInterval(row, row);
                    if (logTableEntryUI.getSelectedRowCount() == 1) {
                        int realRow = logTableEntryUI.convertRowIndexToModel(row);
                        LogEntity logentry = logEntries.get(realRow);

                        if (e.getComponent() instanceof LogTableEntryUI) {
                            new ContextMenuUI(logentry, logEntries, originalRequestViewer, originalResponseViewer, logTableEntriesUI, logTableEntryUI, callbacks, isAnalysisRunning)
                                    .show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        };
        logTableEntryUI.addMouseListener(contextMenu);

        ListSelectionModel listSelectionModel = logTableEntryUI.getSelectionModel();
        logTableEntryUI.setSelectionModel(listSelectionModel);

        return new JScrollPane(logTableEntryUI);
    }

    private JSplitPane createLogger_RequestResponse() {
        JSplitPane requestResponseSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        requestResponseSplitPane.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        JPanel requestResponsePanel = new JPanel();
        requestResponsePanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        requestResponsePanel.add(requestResponseSplitPane);

        JPanel originalRequestPanel = new JPanel();
        JLabel originalRequestLabel = new JLabel("Request");
        originalRequestLabel.setFont(new Font("Lucida Grande", Font.BOLD, 14)); // NOI18N
        originalRequestLabel.setForeground(new Color(255, 102, 51));
        originalRequestPanel.add(originalRequestLabel);
        originalRequestPanel.add(originalRequestViewer.getComponent());

        JPanel originalResponsePanel = new JPanel();
        JLabel originalResponseLabel = new JLabel("Response");
        originalResponseLabel.setFont(new Font("Lucida Grande", Font.BOLD, 14)); // NOI18N
        originalResponseLabel.setForeground(new Color(255, 102, 51));
        originalResponsePanel.add(originalResponseLabel);
        originalResponsePanel.add(originalResponseViewer.getComponent());

        originalRequestPanel.setLayout(new BoxLayout(originalRequestPanel, BoxLayout.PAGE_AXIS));
        originalResponsePanel.setLayout(new BoxLayout(originalResponsePanel, BoxLayout.PAGE_AXIS));

        requestResponseSplitPane.setLeftComponent(originalRequestPanel);
        requestResponseSplitPane.setRightComponent(originalResponsePanel);
        requestResponseSplitPane.setResizeWeight(0.50);
        requestResponseSplitPane.setMaximumSize(new Dimension(2000, 500));

        return requestResponseSplitPane;
    }

    private JButton createLogger_ClearLogs(JScrollPane scrollPaneLogger) {
        JButton btnClearLogs = new JButton("Clear Logs");
        btnClearLogs.addActionListener(e -> {
            int dialog = JOptionPane.showConfirmDialog(null, "Delete ALL the logs in the list?");
            if (dialog == JOptionPane.YES_OPTION) {
                logEntries.clear();
                logTableEntriesUI.clear();

                originalResponseViewer.setText(new byte[0]);
                originalResponseViewer.setSearchExpression("");
                originalRequestViewer.setText(new byte[0]);
            }

            scrollPaneLogger.validate();
            scrollPaneLogger.repaint();
        });

        btnClearLogs.putClientProperty("analysisDependent", "1");
        return btnClearLogs;
    }

    /**
     * Export logs menu, to export the log entries to file
     * @return JMenuBar for exporting logs
     */
    private JMenuBar createLogger_ExportLogs() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Export logs...");
        menu.putClientProperty("analysisDependent", "1");

        JMenuItem itemToCSV = new JMenuItem("to CSV");
        itemToCSV.addActionListener(actionEvent -> {
            List<String> lines = new ArrayList<>();

            lines.add(String.format("\"%s\",\"%s\",\"%s\"",
                    this.logTableEntriesUI.getColumnNameFormatted(0),
                    this.logTableEntriesUI.getColumnNameFormatted(1),
                    this.logTableEntriesUI.getColumnNameFormatted(3)));

            // values
            for (int i = 0; i < this.logTableEntriesUI.getRowCount(); i++) {
                String request_id = this.logTableEntriesUI.getValueAt(i, 0).toString();
                String url = this.logTableEntriesUI.getValueAt(i, 1).toString();
                String matchEscaped = this.logTableEntriesUI.getValueAt(i, 3).toString().replaceAll("\"", "\"\"");
                lines.add(String.format("\"%s\",\"%s\",\"%s\"", request_id, url, matchEscaped));
            }

            Utils.saveToFile("csv", lines);
        });
        menu.add(itemToCSV);

        JMenuItem itemToJSON = new JMenuItem("to JSON");
        itemToJSON.addActionListener(actionEvent -> {
            List<JsonObject> lines = new ArrayList<>();

            String prop1 = this.logTableEntriesUI.getColumnNameFormatted(0);
            String prop2 = this.logTableEntriesUI.getColumnNameFormatted(1);
            String prop3 = this.logTableEntriesUI.getColumnNameFormatted(3);

            // values
            for (int i = 0; i < this.logTableEntriesUI.getRowCount(); i++) {
                JsonObject obj = new JsonObject();
                obj.addProperty(prop1, this.logTableEntriesUI.getValueAt(i, 0).toString());
                obj.addProperty(prop2, this.logTableEntriesUI.getValueAt(i, 1).toString());
                obj.addProperty(prop3, this.logTableEntriesUI.getValueAt(i, 3).toString());
                lines.add(obj);
            }

            GsonBuilder builder = new GsonBuilder().disableHtmlEscaping();
            Gson gson = builder.create();
            Type tListEntries = new TypeToken<ArrayList<JsonObject>>() {}.getType();
            Utils.saveToFile("json", List.of(gson.toJson(lines, tListEntries)));
        });
        menu.add(itemToJSON);

        menuBar.add(menu);
        return menuBar;
    }

    /**
     * Function to call before an analysis start.
     * It performs operations required before an analysis.
     */
    private void preAnalysisOperations() {
        // disable components that shouldn't be used while scanning
        Utils.setEnabledRecursiveComponentsWithProperty(this.getSplitPane(), false, "analysisDependent");
    }

    /**
     * Function to call after an analysis start.
     * It performs operations required after an analysis.
     */
    private void postAnalysisOperations() {
        // re-enable components not usable while scanning
        Utils.setEnabledRecursiveComponentsWithProperty(this.getSplitPane(), true, "analysisDependent");
    }

    private List<JComponent> createLogger_AnalyzeHTTPHistory(JPanel tabPanelLogger) {
        final String textAnalysisStart = "Analyze HTTP History";
        final String textAnalysisStop = "Stop analysis";
        final String textAnalysisStopping = "Stopping the analysis...";

        JButton btnAnalysis = new JButton(textAnalysisStart);
        JProgressBar progressBar = new JProgressBar(0, 1);

        btnAnalysis.addActionListener(actionEvent -> {
            if (!isAnalysisRunning) {
                preAnalysisOperations();
                this.isAnalysisRunning = true;
                this.analyzeProxyHistoryThread = new Thread(() -> {
                    String previousText = btnAnalysis.getText();
                    btnAnalysis.setText(textAnalysisStop);
                    logTableEntryUI.setAutoCreateRowSorter(false);

                    burpLeaksScanner.analyzeProxyHistory(progressBar);

                    btnAnalysis.setText(previousText);
                    logTableEntryUI.setAutoCreateRowSorter(true);
                    this.analyzeProxyHistoryThread = null;
                    this.isAnalysisRunning = false;
                    postAnalysisOperations();
                });
                this.analyzeProxyHistoryThread.start();

                logTableEntryUI.validate();
                logTableEntryUI.repaint();
            } else {
                if (Objects.isNull(this.analyzeProxyHistoryThread)) return;

                btnAnalysis.setEnabled(false);
                btnAnalysis.setText(textAnalysisStopping);
                burpLeaksScanner.setInterruptScan(true);

                new Thread(() -> {
                    try {
                        this.analyzeProxyHistoryThread.join();
                        burpLeaksScanner.setInterruptScan(false);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    btnAnalysis.setEnabled(true);
                    btnAnalysis.setText(textAnalysisStart);
                }).start();
            }

            tabPanelLogger.validate();
            tabPanelLogger.repaint();
        });

        return Arrays.asList(btnAnalysis, progressBar);
    }

    private JPanel createOptionsPanel() {
        JPanel tabPaneOptions = new JPanel();
        tabPaneOptions.setLayout(new BoxLayout(tabPaneOptions, BoxLayout.Y_AXIS));

        // Configuration
        JPanel configurationsPanel = createOptions_Configurations();
        tabPaneOptions.add(configurationsPanel);
        tabPaneOptions.add(new JSeparator());

        // Regex
        createOptions_Regex(
                tabPaneOptions,
                createOptions_Regex_Title(),
                RegexSeeder::getGeneralRegexes,
                this.generalRegexList,
                ProxyItemSection.getDefault())
            .forEach(tabPaneOptions::add);
        tabPaneOptions.add(new JSeparator());

        // Extensions
        createOptions_Regex(
                tabPaneOptions,
                createOptions_Extensions_Title(),
                RegexSeeder::getExtensionRegexes,
                this.extensionsRegexList,
                EnumSet.of(ProxyItemSection.REQ_URL))
            .forEach(tabPaneOptions::add);

        tabPaneOptions.putClientProperty("analysisDependent", "1");
        return tabPaneOptions;
    }

    private JPanel createOptions_ParagraphSection(String title, String description) {
        JPanel titlePanelRegex = new JPanel();
        titlePanelRegex.setAlignmentX(Component.RIGHT_ALIGNMENT);
        titlePanelRegex.setPreferredSize(new Dimension(1000, 60));
        titlePanelRegex.setMaximumSize(new Dimension(1000, 60));
        titlePanelRegex.setLayout(new BoxLayout(titlePanelRegex, BoxLayout.Y_AXIS));

        JLabel jLabelRegexList = new JLabel();
        jLabelRegexList.setFont(new Font("Lucida Grande", Font.BOLD, 14)); // NOI18N
        jLabelRegexList.setForeground(new Color(255, 102, 51));
        jLabelRegexList.setText(title);

        JLabel jlabelSpace = new JLabel();
        jlabelSpace.setText(" \n");

        JLabel jLabelRegexDescription = new JLabel();
        jLabelRegexDescription.setText(description);

        titlePanelRegex.add(jlabelSpace);
        titlePanelRegex.add(jLabelRegexList);
        titlePanelRegex.add(jLabelRegexDescription);

        return titlePanelRegex;
    }

    private JPanel createOptions_Regex_Title() {
        return createOptions_ParagraphSection("Regex List", "This section contains general regexes that try to match only within the response.");
    }

    private JPanel createOptions_Extensions_Title() {
        return createOptions_ParagraphSection("Extensions List", "This section contains regexes for filename extensions. These regexes try to match only the URL of the request.");
    }

    private List<JComponent> createOptions_Regex(
            JPanel tabPaneOptions,
            JPanel optionsTitlePanel,
            Supplier<List<RegexEntity>> resetRegexSeeder,
            List<RegexEntity> regexEntities,
            EnumSet<ProxyItemSection> newRegexesSections)
    {
        var ctx = new Object() {
            final List<RegexEntity> regexList = regexEntities;
        };

        OptionsRegexTableModelUI modelReg = new OptionsRegexTableModelUI(ctx.regexList);
        JTable optionsRegexTable = new JTable(modelReg);
        JPanel buttonPanelRegex = new JPanel();

        JButton btnEnableAll = new JButton("Enable all");
        buttonPanelRegex.add(btnEnableAll);
        btnEnableAll.addActionListener(actionEvent -> {
            ctx.regexList.forEach(regex -> regex.setActive(true));

            modelReg.fireTableDataChanged();

            tabPaneOptions.validate();
            tabPaneOptions.repaint();
        });

        JButton btnDisableAll = new JButton("Disable all");
        buttonPanelRegex.add(btnDisableAll);
        btnDisableAll.addActionListener(actionEvent -> {
            ctx.regexList.forEach(regex -> regex.setActive(false));

            modelReg.fireTableDataChanged();

            tabPaneOptions.validate();
            tabPaneOptions.repaint();
        });

        JButton btnResetRegex = new JButton("Reset");
        buttonPanelRegex.add(btnResetRegex);
        btnResetRegex.addActionListener(actionEvent -> {
            // start from the end and iterate to the beginning to delete because when you delete,
            // it is deleting elements from data, and you are skipping some rows when you iterate
            // to the next element (via i++)
            int dialog = JOptionPane.showConfirmDialog(null, "By confirming, you will return to init setting");
            if (dialog != JOptionPane.YES_OPTION) return;

            if (ctx.regexList.size() > 0) {
                ctx.regexList.subList(0, ctx.regexList.size()).clear();
            }

            ctx.regexList.clear();
            ctx.regexList.addAll(resetRegexSeeder.get());
            modelReg.fireTableDataChanged();

            tabPaneOptions.validate();
            tabPaneOptions.repaint();
        });

        JButton btnNewRegex = new JButton("New");
        buttonPanelRegex.add(btnNewRegex);
        btnNewRegex.addActionListener(actionEvent -> {
            String[] labels = {"Regex: ", "Description: "};
            //Create and populate the panel.
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            JLabel labelSummary = new JLabel("The new regex will only match: " + newRegexesSections.toString(), JLabel.TRAILING);
            mainPanel.add(labelSummary);
            JPanel inputPanel = new JPanel(new SpringLayout());
            mainPanel.add(inputPanel);
            JLabel labelExpression = new JLabel(labels[0], JLabel.TRAILING);
            inputPanel.add(labelExpression);
            JTextField textFieldReg = new JTextField(10);
            labelExpression.setLabelFor(textFieldReg);
            inputPanel.add(textFieldReg);
            JLabel labelDescription = new JLabel(labels[1], JLabel.TRAILING);
            inputPanel.add(labelDescription);
            JTextField textFieldDesc = new JTextField(10);
            labelDescription.setLabelFor(textFieldDesc);
            inputPanel.add(textFieldDesc);
            //Lay out the panel.
            SpringUtilities.makeCompactGrid(inputPanel,
                    labels.length, 2, //rows, cols
                    6, 6,        //initX, initY
                    6, 6);       //xPad, yPad
            int returnValue = JOptionPane.showConfirmDialog(tabPaneOptions, mainPanel, "Add a regular expression", JOptionPane.YES_NO_OPTION);
            if (returnValue != JOptionPane.YES_OPTION) return;

            String expression = textFieldReg.getText();
            String description = textFieldDesc.getText();

            int row = ctx.regexList.size();
            ctx.regexList.add(new RegexEntity(description, expression, true, newRegexesSections));
            modelReg.fireTableRowsInserted(row, row);

            tabPaneOptions.validate();
            tabPaneOptions.repaint();
        });

        JButton btnDeleteRegex = new JButton("Delete");
        buttonPanelRegex.add(btnDeleteRegex);
        btnDeleteRegex.addActionListener(actionEvent -> {
            int rowIndex = optionsRegexTable.getSelectedRow();
            if (rowIndex == -1) return;
            int realRow = optionsRegexTable.convertRowIndexToModel(rowIndex);
            ctx.regexList.remove(realRow);

            modelReg.fireTableRowsDeleted(realRow, realRow);

            tabPaneOptions.validate();
            tabPaneOptions.repaint();
        });

        JButton btnClearRegex = new JButton("Clear");
        buttonPanelRegex.add(btnClearRegex);
        btnClearRegex.addActionListener(actionEvent -> {
            int dialog = JOptionPane.showConfirmDialog(null, "Delete ALL the regex in the list?");
            if (dialog != JOptionPane.YES_OPTION) return;

            if (ctx.regexList.size() > 0) {
                ctx.regexList.subList(0, ctx.regexList.size()).clear();
                modelReg.fireTableDataChanged();

                tabPaneOptions.validate();
                tabPaneOptions.repaint();
            }
        });

        JButton btnOpenRegex = new JButton("Open");
        buttonPanelRegex.add(btnOpenRegex);
        btnOpenRegex.addActionListener(actionEvent -> {
            JFileChooser chooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter(".csv","csv");
            chooser.setFileFilter(filter);
            int returnVal = chooser.showOpenDialog(buttonPanelRegex);
            if (returnVal != JFileChooser.APPROVE_OPTION) return;

            File selectedFile = chooser.getSelectedFile();
            System.out.println("Importing regex file (" + chooser.getTypeDescription(selectedFile) + "): " + selectedFile.getName());
            try {
                Scanner scanner = new Scanner(chooser.getSelectedFile());
                StringBuilder alreadyAdded = new StringBuilder();
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();

                    Matcher matcher = RegexEntity.checkRegexEntityFromCSV(line);
                    if (!matcher.find()) continue;

                    String description = matcher.group(1);
                    String regex = matcher.group(2);

                    RegexEntity newRegexEntity = new RegexEntity(description, regex, true, newRegexesSections);

                    if (!ctx.regexList.contains(newRegexEntity)) {
                        ctx.regexList.add(newRegexEntity);
                    } else {
                        alreadyAdded.append(description).append(" - ").append(regex).append("\n");
                    }
                }
                modelReg.fireTableDataChanged();

                if (!(alreadyAdded.toString().isBlank())) {
                    alreadyAdded.insert(0, "These regexes are already present:\n");
                    JDialog alreadyAddedDialog = new JDialog();
                    JOptionPane.showMessageDialog(alreadyAddedDialog, alreadyAdded.toString(), "Already Added Alert", JOptionPane.INFORMATION_MESSAGE);
                    alreadyAddedDialog.setVisible(true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                tabPaneOptions.validate();
                tabPaneOptions.repaint();
            }
        });

        JButton btnSaveRegex = new JButton("Save");
        buttonPanelRegex.add(btnSaveRegex);
        btnSaveRegex.addActionListener(actionEvent -> {
            List<String> lines = new ArrayList<>();

            // header
            lines.add(String.format("\"%s\",\"%s\"",
                    modelReg.getColumnNameFormatted(2),
                    modelReg.getColumnNameFormatted(1)));

            // values
            int rowCount = modelReg.getRowCount();
            for (int i = 0; i < rowCount; i++) {
                String description = modelReg.getValueAt(i, 2).toString().replaceAll("\"", "\"\"");
                String regex = modelReg.getValueAt(i, 1).toString().replaceAll("\"", "\"\"");
                lines.add(String.format("\"%s\",\"%s\"", description, regex));
            }
            Utils.saveToFile("csv", lines);
        });

        optionsRegexTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPaneRegOptions = new JScrollPane(optionsRegexTable);
        optionsRegexTable.getColumnModel().getColumn(0).setMinWidth(80);
        optionsRegexTable.getColumnModel().getColumn(0).setMaxWidth(80);
        optionsRegexTable.getColumnModel().getColumn(0).setPreferredWidth(80);

        callbacks.customizeUiComponent(optionsRegexTable);
        callbacks.customizeUiComponent(scrollPaneRegOptions);

        return Arrays.asList(optionsTitlePanel, buttonPanelRegex, scrollPaneRegOptions);
    }

    private JPanel createOptions_Configurations() {
        JPanel configurationsPanel = new JPanel();
        configurationsPanel.setLayout(new BoxLayout(configurationsPanel, BoxLayout.X_AXIS));
        configurationsPanel.setAlignmentX(JPanel.RIGHT_ALIGNMENT);

        JPanel scopePanel = createOptions_Configuration_Filters();
        configurationsPanel.add(scopePanel);
        JPanel scannerPanel = createOptions_Configuration_Scanner();
        configurationsPanel.add(scannerPanel);

        return configurationsPanel;
    }

    private JPanel createOptions_Configuration_Filters() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//        panel.setMaximumSize(new Dimension(500,100));
        panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                "Filters",
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                new Font("Lucida Grande", Font.BOLD, 14), // NOI18N
                new Color(255, 102, 51)
                ));

        JCheckBox inScopeCheckbox = new JCheckBox("Show only in-scope items");
        inScopeCheckbox.getModel().setSelected(MainUI.inScopeCheckbox);
        inScopeCheckbox.addActionListener(e -> MainUI.inScopeCheckbox = inScopeCheckbox.getModel().isSelected());
        panel.add(inScopeCheckbox);

        JCheckBox skipMaxSizeCheckbox = new JCheckBox("Skip responses over set size");
        skipMaxSizeCheckbox.getModel().setSelected(MainUI.skipMaxSizeCheckbox);
        skipMaxSizeCheckbox.addActionListener(e -> MainUI.skipMaxSizeCheckbox = skipMaxSizeCheckbox.getModel().isSelected());
        panel.add(skipMaxSizeCheckbox);

        JCheckBox skipMediaTypeCheckbox = new JCheckBox("Skip media-type responses (images, videos, archives, ...)");
        skipMediaTypeCheckbox.getModel().setSelected(MainUI.skipMediaTypeCheckbox);
        skipMediaTypeCheckbox.addActionListener(e -> MainUI.skipMediaTypeCheckbox = skipMediaTypeCheckbox.getModel().isSelected());
        panel.add(skipMediaTypeCheckbox);

        return panel;
    }

    private JPanel createOptions_Configuration_Scanner() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//        panel.setMaximumSize(new Dimension(500,100));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.gray, 1),
                "Scanner",
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION,
                new Font("Lucida Grande", Font.BOLD, 14), // NOI18N
                new Color(255, 102, 51)
        ));

        panel.add(createOptions_numThreads());
        panel.add(createOptions_maxSizeFilter());

        return panel;
    }

    private JPanel createOptions_numThreads() {
        JPanel mainGroup = new JPanel();
        mainGroup.setLayout(new BoxLayout(mainGroup, BoxLayout.Y_AXIS));
        mainGroup.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel numThreads = new JPanel();
        JLabel numThreadsDescription = new JLabel("Current number of threads: ");
        JLabel numThreadsCurrent = new JLabel(String.valueOf(this.burpLeaksScanner.getNumThreads()));
        numThreads.setLayout(new FlowLayout(FlowLayout.LEFT));
        numThreads.add(numThreadsDescription);
        numThreads.add(numThreadsCurrent);

        JPanel updateNumThreads = new JPanel();
        JLabel updateNumThreadsDescription = new JLabel("Update number of threads (1-128): ");
        JTextField updateNumThreadsField = new JTextField(4);
        JButton updateNumThreadsSet = new JButton("Set");
        updateNumThreadsSet.addActionListener(e -> {
            try {
                int newThreadNumber = Integer.parseInt(updateNumThreadsField.getText());
                if (newThreadNumber < 1 || newThreadNumber > 128)
                    throw new NumberFormatException("Number not in the expected range");

                this.burpLeaksScanner.setNumThreads(newThreadNumber);
                numThreadsCurrent.setText(String.valueOf(this.burpLeaksScanner.getNumThreads()));
                updateNumThreadsField.setText("");
            } catch (NumberFormatException ignored) {
            }
        });
        updateNumThreads.setLayout(new FlowLayout(FlowLayout.LEFT));
        updateNumThreads.add(updateNumThreadsDescription);
        updateNumThreads.add(updateNumThreadsField);
        updateNumThreads.add(updateNumThreadsSet);

        mainGroup.add(numThreads);
        mainGroup.add(updateNumThreads);
        return mainGroup;
    }

    //TODO enable/disable max size input box with MainUI.skipMaxSizeCheckbox
    private JPanel createOptions_maxSizeFilter() {
        JPanel mainGroup = new JPanel();
        mainGroup.setLayout(new BoxLayout(mainGroup, BoxLayout.Y_AXIS));
        mainGroup.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel maxSize = new JPanel();
        JLabel maxSizeDescription = new JLabel("Current max response size (bytes): ");
        JLabel maxSizeCurrent = new JLabel(String.valueOf(MainUI.maxSizeValue));
        maxSize.setLayout(new FlowLayout(FlowLayout.LEFT));
        maxSize.add(maxSizeDescription);
        maxSize.add(maxSizeCurrent);

        JPanel updateMaxSize = new JPanel();
        JLabel updateMaxSizeDescription = new JLabel("Update max response size (bytes): ");
        JTextField updateMaxSizeField = new JTextField(4);
        JButton updateMaxSizeSet = new JButton("Set");
        updateMaxSizeSet.addActionListener(e -> {
            try {
                int newMaxSizeValue = Integer.parseInt(updateMaxSizeField.getText());
                if (newMaxSizeValue < 1)
                    throw new NumberFormatException("Size must be >= 1");

                MainUI.maxSizeValue = newMaxSizeValue;
                maxSizeCurrent.setText(String.valueOf(MainUI.getMaxSizeValueOption()));
                updateMaxSizeField.setText("");
            } catch (NumberFormatException ignored) {
            }
        });
        updateMaxSize.setLayout(new FlowLayout(FlowLayout.LEFT));
        updateMaxSize.add(updateMaxSizeDescription);
        updateMaxSize.add(updateMaxSizeField);
        updateMaxSize.add(updateMaxSizeSet);

        mainGroup.add(maxSize);
        mainGroup.add(updateMaxSize);

        return mainGroup;
    }

    @Override
    public String getTabCaption() {
        return getNameExtension();
    }

    @Override
    public Component getUiComponent() {
        return getSplitPane();
    }
}
