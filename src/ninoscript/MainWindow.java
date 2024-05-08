package ninoscript;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

import ninoscript.ScriptReader.BlockData;

@SuppressWarnings("serial")
public class MainWindow extends JFrame {
	JMenuBar menuBar = new JMenuBar();
	JMenu optionsMenu = new JMenu("Options");
	JMenuItem loadFiles = new JMenuItem("Load");
	JButton saveButton = new JButton("Save changes");
	
	DefaultListModel<ScriptReader> fileListModel = new DefaultListModel<ScriptReader>();
	SpinnerNumberModel blockSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
	JLabel blockMaxLabel = new JLabel("of 0");
	
	JList<ScriptReader> fileList = new JList<ScriptReader>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	JTextArea originalText = new JTextArea(6, 30);
	JList<Integer> originalTextLenLists = new JList<Integer>();
	JTextArea newText = new JTextArea(6, 30);
	JList<Integer> newTextLenLists = new JList<Integer>();
	JTextField originalSpeakerField = new JTextField(10);
	JTextField newSpeakerField = new JTextField(10);
	
	LengthPanel originalLengths = new LengthPanel();
	LengthPanel newLengths = new LengthPanel();
	
	private Map<ScriptReader, File> scriptMap = new HashMap<ScriptReader, File>();
	private ScriptReader currentScript;
	
	private GridBagConstraints gbcon = new GridBagConstraints();
	
	public MainWindow() {
		super("script edit");
		setLayout(new GridBagLayout());
		setSize(600, 400);
		
		optionsMenu.add(loadFiles);
		menuBar.add(optionsMenu);
		setJMenuBar(menuBar);
		
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		originalText.setEditable(false);
		originalSpeakerField.setEditable(false);
		
		originalText.setMinimumSize(originalText.getPreferredSize());
		newText.setMinimumSize(newText.getPreferredSize());
		originalSpeakerField.setMinimumSize(originalSpeakerField.getPreferredSize());
		newSpeakerField.setMinimumSize(newSpeakerField.getPreferredSize());
	
		JPanel blockPanel = new JPanel();
		blockPanel.add(new JLabel("Block number:"));
		blockPanel.add(blockSpinner);
		blockPanel.add(blockMaxLabel);
		
		JPanel originalTextPanel = new JPanel();
		originalTextPanel.setLayout(new BoxLayout(originalTextPanel, BoxLayout.X_AXIS));
		originalTextPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Original text"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		originalTextPanel.add(originalText);
		originalTextPanel.add(originalLengths);
		
		JPanel newTextPanel = new JPanel();
		newTextPanel.setLayout(new BoxLayout(newTextPanel, BoxLayout.X_AXIS));
		newTextPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Modified text"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		newTextPanel.add(newText);
		newTextPanel.add(newLengths);
		
		JPanel originalSpeakerPanel = new JPanel();
		originalSpeakerPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Original speaker"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		originalSpeakerPanel.add(originalSpeakerField);
		
		JPanel newSpeakerPanel = new JPanel();
		newSpeakerPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Modified speaker"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		newSpeakerPanel.add(newSpeakerField);
		
		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setMinimumSize(new Dimension(fileList.getPreferredScrollableViewportSize().width, 
				fileList.getPreferredScrollableViewportSize().height));
		
		gbcon.gridheight = 4;
		gbcon.fill = GridBagConstraints.BOTH;
		addGB(fileScroll, 0, 0);
		
		gbcon.fill = GridBagConstraints.NONE;
		gbcon.gridheight = 1;
		
		gbcon.anchor = GridBagConstraints.WEST;
		addGB(blockPanel, 1, 0);
		addGB(originalTextPanel, 1, 1);
		addGB(newTextPanel, 1, 2);
			
		addGB(saveButton, 1, 3);
		
		gbcon.anchor = GridBagConstraints.NORTHWEST;
		addGB(originalSpeakerPanel, 2, 1);
		addGB(newSpeakerPanel, 2, 2);
		
		initListeners();
		
		//setSize(getPreferredSize());
		setVisible(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
		    //
		}
		
		MainWindow w = new MainWindow();
	}

	public void initListeners() {
		loadFiles.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			c.setFileFilter(new BinFileFilter());
			c.showOpenDialog(this);
			File file = c.getSelectedFile();
			
			if(file != null) {
				if(file.isDirectory()) {
					loadFolder(file);
				}
				else {
					loadFile(file);
				}
			}
		});
		
		fileList.addListSelectionListener(event -> {
			
		});
	}
	
	private void loadFile(File file) { //dump old list, load one file
		ScriptReader script = new ScriptReader(file);
		
		fileListModel.clear();
		scriptMap.clear();
		scriptMap.put(script, file);
		fileListModel.addElement(script);
		fileList.setSelectedIndex(0);
	}
	
	private void loadFolder(File dir) {
		fileListModel.clear();
		scriptMap.clear();
		FileWriter fw;
		PrintWriter pw;
		
		List<String> speakerStrings = new ArrayList<String>();
		int largestBlock = 0;
		
		try {
			fw = new FileWriter("output.txt");
		}
		catch (IOException i) {
			return;
		}
		
		pw = new PrintWriter(fw, true);
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			ScriptReader script = new ScriptReader(file);
			scriptMap.put(script, file);
			fileListModel.addElement(script);
			for(BlockData data : script.getBlockList()) {
				if(data.getFullBlockLength() > largestBlock) {
					largestBlock = data.getFullBlockLength();
				}
				
				if(data.getSpeakerBytes() != null) {
					if(!speakerStrings.contains(data.getSpeakerBytesString())) {
						speakerStrings.add(data.getSpeakerBytesString());
					}
					
				}
			}
		}
		
		pw.println(largestBlock);
		pw.println("");
		for(String string : speakerStrings) {
			pw.println(string);
		}
		
		fileList.setSelectedIndex(0);
		
		pw.close();
		try {
			fw.close();
		} catch (IOException e) {
		}
	}
	
	private void addGB(Component comp, int x, int y) {
		gbcon.gridx = x;
		gbcon.gridy = y;
		add(comp, gbcon);
	}
	
	private static class LengthPanel extends JPanel {
		private JLabel lengthLabel1 = new JLabel();
		private JLabel lengthLabel2 = new JLabel();
		private JLabel lengthLabel3 = new JLabel();
		private JLabel lengthLabel4 = new JLabel();
		private JLabel lengthLabel5 = new JLabel();
		private JLabel lengthLabel6 = new JLabel();
		List<JLabel> labels = new ArrayList<JLabel>(Arrays.asList(lengthLabel1, lengthLabel2, lengthLabel3,
				lengthLabel4, lengthLabel5, lengthLabel6));
		
		public LengthPanel() {
			setLayout(new BoxLayout(this,  BoxLayout.Y_AXIS));
			add(lengthLabel1);
			add(lengthLabel2);
			add(lengthLabel3);
			add(lengthLabel4);
			add(lengthLabel5);
			add(lengthLabel6);
		}
		
		public void setLabelText(int label, int content) {
			labels.get(label).setText(Integer.toString(content));
		}
		
		public void setLabelVisibility(int label, boolean isVisible) {
			labels.get(label).setVisible(isVisible);
		}
	}
	
	//cursed
	public static class BinFileFilter extends FileFilter implements java.io.FileFilter {
		public boolean accept(File file) {
			if(file.isDirectory()) {
				return true;
			}
			
			String extension = file.getName();
			int i = extension.lastIndexOf('.');
			if (i > 0 && i < extension.length() - 1) {
				extension = extension.substring(i+1).toLowerCase();
	        }
			else {
				return false;
			}
			
			if(extension.equals("bin")) {
				return true;
			}
			return false;
		}

		public String getDescription() {
			return "Script files (.bin)";
		}	
	}
}