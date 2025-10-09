package ninoscript;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;


import n2dhandler.N2D;
import n2dhandler.TileMaker;
import ninoscript.ConvoSubBlockData.*;
import ninoscript.DatParser.DatType;
import ninoscript.ScriptParser.ConvoMagic;

@SuppressWarnings("serial")
public class MainWindow extends JFrame {
	public enum Mode {
		SCRIPTFILE,
		ITEMLINKINFO,
		GENERALDAT
	}
	
	JMenuBar menuBar = new JMenuBar();
	JMenu optionsMenu = new JMenu("Options");
	JMenuItem loadFiles = new JMenuItem("Load");
	JMenuItem loadF10 = new JMenuItem("Load font10");
	JMenuItem loadF12 = new JMenuItem("Load font12");
	JButton saveFileButton = new JButton("Save changes to file");
	
	JMenu utilitiesMenu = new JMenu("Utilities");
	JMenuItem findAllMatches = new JMenuItem("Find all matches");
	JMenuItem generateN2DImage = new JMenuItem("Generate images from .n2d");
	JMenuItem tileMaker = new JMenuItem("Make tiles from images");
	JMenuItem pngToBtx = new JMenuItem("Convert PNG to BTX");
	
	DefaultListModel<String> fileListModel = new DefaultListModel<String>();
	SpinnerNumberModel blockSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
	JLabel blockMaxLabel = new JLabel("of 0");
	
	JList<String> fileList = new JList<String>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	
	JList<Integer> originalTextLenLists = new JList<Integer>();
	
	JList<Integer> newTextLenLists = new JList<Integer>();
	JCheckBox scriptingCheck = new JCheckBox("Hide extra text data");
	
	DefaultComboBoxModel<String> idComboModel = new DefaultComboBoxModel<String>();
	JComboBox<String> convoIdCombo = new JComboBox<String>(idComboModel);
	
	ButtonGroup fontGroup = new ButtonGroup();
	JRadioButton f10Button = new JRadioButton("font10");
	JRadioButton f12Button = new JRadioButton("font12");
	
	RegularTextPanel regularTextPanel = new RegularTextPanel();
	MultipleChoicePanel multipleChoicePanel = new MultipleChoicePanel();
	DataPanel currentPanel = regularTextPanel;
	
	private ScriptParserDataAdapter spAdapter = new ScriptParserDataAdapter();
	private ItemLinkInfoDataAdapter ilAdapter = new ItemLinkInfoDataAdapter();
	private DatParserDataAdapter datAdapter = new DatParserDataAdapter();
	private DataAdapter currentAdapter = spAdapter;
	private Mode mode = Mode.SCRIPTFILE;
	
	private boolean updateComponents = true;
	
	private Map<String, Integer> currentFontMap = null;
	private Map<String, Integer> font10Map = null;
	private Map<String, Integer> font12Map = null;
	
	private GridBagConstraints gbcon = new GridBagConstraints();
	
	public MainWindow() {
		super("script edit");
		setLayout(new GridBagLayout());
		setSize(700, 400);
		
		optionsMenu.add(loadFiles);
		optionsMenu.add(loadF10);
		optionsMenu.add(loadF12);
		menuBar.add(optionsMenu);
		
		utilitiesMenu.add(findAllMatches);
		utilitiesMenu.add(generateN2DImage);
		utilitiesMenu.add(tileMaker);
		utilitiesMenu.add(pngToBtx);
		menuBar.add(utilitiesMenu);
		setJMenuBar(menuBar);
		
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setSelectionModel(new NoDeselectionModel());
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(f10Button);
		buttonPanel.add(f12Button);
		
		fontGroup.add(f10Button);
		fontGroup.add(f12Button);
		f10Button.setActionCommand("f10");
		f12Button.setActionCommand("f12");
		f10Button.setEnabled(false);
		f12Button.setEnabled(false);
		
		//set up subpanels
		JPanel blockPanel = new JPanel();
		blockPanel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 5, 0, 5);
		c.gridy = 0;
		c.gridx = 0;
		blockPanel.add(new JLabel("Block number:"), c);
		c.insets = new Insets(0, 0, 0, 5);
		c.gridx = GridBagConstraints.RELATIVE;
		c.ipadx = 10;
		blockPanel.add(blockSpinner, c);
		c.gridx = GridBagConstraints.RELATIVE;
		c.ipadx = 5;
		blockPanel.add(blockMaxLabel, c);
		
		JPanel convoIndexPanel = new JPanel();
		convoIndexPanel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.insets = new Insets(0, 5, 0, 5);
		c.gridy = 0;
		c.gridx = 0;
		convoIndexPanel.add(new JLabel("Convo number:"), c);
		c.insets = new Insets(0, 0, 0, 5);
		c.gridx = GridBagConstraints.RELATIVE;
		c.ipadx = 10;
		convoIndexPanel.add(convoIdCombo, c);
		
		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setMinimumSize(new Dimension(fileList.getPreferredScrollableViewportSize().width, 
				fileList.getPreferredScrollableViewportSize().height));
		
		//actually adding to mainwindow
		gbcon.anchor = GridBagConstraints.NORTHWEST;
		gbcon.gridheight = 5;
		gbcon.fill = GridBagConstraints.BOTH;
		addGB(fileScroll, 0, 0);
		
		gbcon.anchor = GridBagConstraints.WEST;
		gbcon.gridheight = 1;
		gbcon.fill = GridBagConstraints.NONE;
		addGB(convoIndexPanel, GridBagConstraints.RELATIVE, 0);
		
		addGB(blockPanel, 1, 1);
		addGB(buttonPanel, GridBagConstraints.RELATIVE, 1);
		addGB(scriptingCheck, GridBagConstraints.RELATIVE, 1);
		
		gbcon.gridwidth = 3;
		gbcon.weighty = 0.7;
		addGB(regularTextPanel, 1, GridBagConstraints.RELATIVE);
		add(multipleChoicePanel, gbcon); //use the same constraints
		
		gbcon.gridwidth = 1;
		gbcon.weighty = 0;
		addGB(saveFileButton, 1, GridBagConstraints.RELATIVE);
		
		initListeners();
		
		multipleChoicePanel.setVisible(false);
		multipleChoicePanel.setMinimumSize(regularTextPanel.getMinimumSize());	
		
		setVisible(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
		    //
		}
		
		new MainWindow();
	}

	public void initListeners() {
		loadFiles.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			//c.setFileFilter(new BinFileFilter());
			c.showOpenDialog(this);
			File file = c.getSelectedFile();
			
			if(file != null) {
				if(file.isDirectory()) {
					//TODO: support folder loading for not scripts?
					loadFolder(file);
				}
				else {
					loadFile(file);
				}
			}
		});
		
		loadF10.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.showOpenDialog(this);
			File file = c.getSelectedFile();
			
			if(file != null) {
				try {
					font10Map = new NFTRMap().parseNFTR(file);
					if(font10Map != null) {
						f10Button.setEnabled(true);
					}
				}
				catch (IOException e) {
				}
			}
		});
		
		loadF12.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.showOpenDialog(this);
			File file = c.getSelectedFile();
			
			if(file != null) {
				try {
					font12Map = new NFTRMap().parseNFTR(file);
					if(font12Map != null) {
						f12Button.setEnabled(true);
					}
				}
				catch (IOException e) {
				}
			}
		});
		
		findAllMatches.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			//c.setFileFilter(new BinFileFilter());
			c.showOpenDialog(this);
			File file = c.getSelectedFile();
			
			if(file != null) {
				findAllMatches(file);
			}
		});
		
		generateN2DImage.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			//c.setFileFilter(new N2dFileFilter());
			c.showOpenDialog(this);
			File targetFile = c.getSelectedFile();
			
			c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			c.setFileFilter(null);
			c.showSaveDialog(this);
			File saveDir = c.getSelectedFile();
			
			if(targetFile != null) {
				if(saveDir == null) {
					saveDir = targetFile.getParentFile();
				}
				
				if(targetFile.isDirectory()) {
					String filter = (String) JOptionPane.showInputDialog(this, "keyword to filter by");
					findMatchingFiles(targetFile, saveDir, filter);
				}
				else {
					try {
						new N2D(targetFile).generateImages(saveDir);
					}
					catch (IOException e) {
						
					}
				}
			}
		});
		
		tileMaker.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileFilter(new PNGFileFilter());
			c.setMultiSelectionEnabled(true);
			c.showOpenDialog(this);
			File[] targetFiles = c.getSelectedFiles();
			
			if(targetFiles.length != 0) {
				c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				c.setFileFilter(null);
				c.setMultiSelectionEnabled(false);
				c.showSaveDialog(this);
				File saveDir = c.getSelectedFile();
				
				if(saveDir == null) {
					saveDir = targetFiles[0].getParentFile();
				}
				TileMaker.makeTiles(targetFiles, saveDir);
			}
			
		});
		
		pngToBtx.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileFilter(new PNGFileFilter());
			c.setDialogTitle("Select a PNG file");
			c.showOpenDialog(this);
			File png = c.getSelectedFile();
			
			if(png == null) {
				return;
			}
			
			c.setFileFilter(null);
			c.setDialogTitle("Select the file to write to");
			c.showSaveDialog(this);
			File btxToWrite = c.getSelectedFile();
			
			String ext = btxToWrite.getName().substring(btxToWrite.getName().lastIndexOf("."));
			
			//TODO: differentiate between bmd0/n3d/tmap
			switch(ext) {
				case ".bmd0":
					byte[] byteBuffer = new byte[(int) btxToWrite.length()];
					FileInputStream inStream;
					IntByteArrayInputStream buffer = new IntByteArrayInputStream(byteBuffer);
					
					try {
						inStream = new FileInputStream(btxToWrite);
						inStream.read(byteBuffer);
						inStream.close();
						
						buffer.mark(-1);
						
						buffer.skip(8);
						int totalSize = buffer.readU32();
						buffer.skip(8);
						
						//int mdlOffset = buffer.readU32();
						int texOffset = buffer.readU32();;
						
						buffer.reset();
						byte[] preTextureBytes = new byte[texOffset]; //header + model bytes
						buffer.read(preTextureBytes);
						
						buffer.reset();
						byte[] textureBytes = new byte[totalSize - texOffset];
						buffer.skip(texOffset);
						buffer.read(textureBytes);
						
						BTX btx = new BTX(textureBytes);
						btx.convertNewImage(png);
						btx.write(btxToWrite, preTextureBytes);
					}
					catch(IOException e) {
						return;
					}
			}
		});
		
		fileList.addListSelectionListener(event -> {
			int index = fileList.getSelectedIndex();
			
			if(index != -1) {
				//might need a is this file still here check
				switch(mode) {
					case SCRIPTFILE:
						currentAdapter.resetCurrentData();
						currentAdapter.updateCurrentScript(fileList.getSelectedValue());
						break;
					case ITEMLINKINFO:
						//do nothing since all the data is already loaded
						break;
					case GENERALDAT:
						
					default:
						break;
				}
				
				populateIDs(); //will force a combo action
			}
			else {
				clearComponents();
			}	
		});
		
		convoIdCombo.addActionListener(event -> {
			if(!updateComponents) {
				return;
			}
			int index = convoIdCombo.getSelectedIndex();
			
			/*
			if(currentScript != null && currentConvo != null) {
				for(ConvoSubBlockData block : currentConvo.getBlockList()) {
					block.resetNewStrings();
				}
			}
			*/
		
			currentAdapter.updateCurrentConversation(index);
			Conversation currentConvo = currentAdapter.getCurrentConversation();
			if(currentConvo != null) {
				System.out.println(currentConvo.getId() + " " + Integer.toHexString(currentConvo.getId()));
			}
			
			int maxBlocks = currentAdapter.getMaxBlocks();
			
			updateComponents = false;
			blockSpinnerModel.setMinimum(-1); //really roundabout way of always forcing the first block to be rendered
			blockSpinnerModel.setValue(-1);
			blockSpinnerModel.setMaximum(maxBlocks);
			blockSpinnerModel.setMinimum(0);
			updateComponents = true;
			blockSpinnerModel.setValue(0); //forces blockspinner update
			
			blockMaxLabel.setText("of " + maxBlocks);
		});
		
		blockSpinner.addChangeListener(event -> {
			if(!updateComponents) {
				return;
			}
			saveText();
			
			int index = (int) blockSpinner.getValue();
			currentAdapter.updateCurrentConvoBlock(index); //updates string for itemlinkinfo
			
			switch(mode) {
				case SCRIPTFILE:
					ConvoSubBlockData currentBlock = spAdapter.getCurrentConvoBlock();
					if(currentBlock == null) {
						return;
					}
					
					if(currentBlock.getMagic() == ConvoMagic.MULTIPLECHOICE) {
						currentPanel = multipleChoicePanel;
						multipleChoicePanel.setVisible(true);
						regularTextPanel.setVisible(false);
					}
					else {
						currentPanel = regularTextPanel;
						multipleChoicePanel.setVisible(false);
						regularTextPanel.setVisible(true);
					}
					
					currentPanel.loadStrings(currentBlock, currentFontMap);
					break;
				case ITEMLINKINFO:
				case GENERALDAT:
					String oldString = currentAdapter.getOriginalMainString();
					
					currentPanel.loadStrings(oldString, currentAdapter.getNewMainString(oldString), currentFontMap);
					break;
			}
				
			setScriptingState();
		});
		
		scriptingCheck.addItemListener(event -> {
			setScriptingState();
		});
		
		saveFileButton.addActionListener(event -> {
			writeFile();
		});
		
		f10Button.addActionListener(event -> {
			currentFontMap = font10Map;
			currentPanel.setCurrentFontMap(currentFontMap);
		});
		
		f12Button.addActionListener(event -> {
			currentFontMap = font12Map;
			currentPanel.setCurrentFontMap(currentFontMap);
		});
	}
	
	private void populateIDs() {
		updateComponents = false;
		idComboModel.removeAllElements();
		
		idComboModel.addAll(currentAdapter.generateIDList());
		updateComponents = true;
		if(idComboModel.getSize() > 0) {
			convoIdCombo.setSelectedIndex(0);
		}
	}
	
	private void findMatchingFiles(File parentDir, File saveDir, String filter) {
		File[] files = parentDir.listFiles((filepath) -> {
			if(filepath.isDirectory()) {
				return true;
			}
			else {
				
				//if(filepath.getName().contains(filter) && filepath.getName().contains(".n2d")) {
					return true;
				//}
				//else {
				//	return false;
				//}
			}
		});
		
		for(File file : files) {
			if(file.isDirectory()) {
				findMatchingFiles(file, saveDir, filter);
			}
			else {
				try {
					System.out.println(file.getAbsolutePath());
					new N2D(file).generateImages(saveDir);
				}
				catch (IOException e) {
					
				}
			}
		}
	}
	
	private void setScriptingState() {
		if(!scriptingCheck.isSelected()) {
			currentPanel.loadOriginalString(currentAdapter);
		}
		else {
			currentPanel.removeStringFormatting(currentAdapter);
		}
	}
	
	private void clearComponents() {
		currentPanel.clearComponents();
		blockSpinner.setValue(0);
		blockMaxLabel.setText("of 0");
	}
	
	private void saveText() {
		currentPanel.saveStrings(currentAdapter);
	}
	
	private void writeFile() {
		saveText();
		int index = fileListModel.indexOf(fileList.getSelectedIndex());
		
		currentAdapter.writeFile();
		
		reloadFile(index);
	}
	
	private void reloadFile(int index) {
		index = index == -1 ? 0 : index;
		
		switch(mode) {
			case SCRIPTFILE:
			case GENERALDAT:
				//fileListModel.set(index, spAdapter.getCurrentScript().toString());
				fileList.clearSelection();
				fileList.setSelectedIndex(index);
				break;
			case ITEMLINKINFO:
				//just reload the entire filelist
				fileListModel.clear();
				fileListModel.addAll(ilAdapter.getLoadedFilenames());
				fileList.setSelectedIndex(0);
				break;
		}		
	}
	
	private void loadFile(File file) { //dump old list, load one file
		boolean foundFile = false;
		Mode previousMode = mode;
		String fileName = file.getName();
		String newListItem = "";
		ByteBuffer magic = ByteBuffer.allocate(4);
		magic.order(ByteOrder.LITTLE_ENDIAN);
		
		//there are no good ways to identify these by content
		switch(fileName.toLowerCase()) {
			case "equipitemlinkinfo.dat":
			case "itemlinkinfo.dat":
				mode = Mode.ITEMLINKINFO;
				ilAdapter.addFile(file);
				currentAdapter = ilAdapter;
				newListItem = fileName;
				foundFile = true;
				break;
			case "iteminfo.dat":
			case "equipiteminfo.dat":
			case "spiteminfo.dat":
				mode = Mode.GENERALDAT;
				datAdapter.addFile(file, DatType.ITEMINFO);
				currentAdapter = datAdapter;
				newListItem = fileName;
				foundFile = true;
				break;
			default:
				break;
		}
		
		if(!foundFile) {
			try {
				FileInputStream stream = new FileInputStream(file);
				stream.read(magic.array());
				stream.close();
			}
			catch (IOException e) {
				return;
			}
			
			switch(magic.getInt()) {
				case 0x001F080A:
					mode = Mode.SCRIPTFILE;
					currentAdapter = spAdapter;
					newListItem = spAdapter.addFile(file);
					foundFile = true;
					break;
				default:
					break;
			}
		}
		
		if(foundFile) {
			if(previousMode != mode) {
				fileListModel.clear();
			}
			if(!fileListModel.contains(newListItem)) { //new addition
				fileListModel.addElement(newListItem);
			}
			fileList.setSelectedValue(newListItem, true);
		}
	}
	
	private void loadFolder(File dir) {
		mode = Mode.SCRIPTFILE;
		currentAdapter = spAdapter;
		spAdapter.getScriptMap().clear();
		fileListModel.clear();
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			fileListModel.addElement(spAdapter.addFile(file));
		}
		fileList.clearSelection();
		fileList.setSelectedIndex(0);
	}
	
	private void findAllMatches(File dir) { //find all matching strings across all files
		//a string, list of files and where that string occurs in that file
		Map<String, Map<String, List<String>>> stringMap = new HashMap<String, Map<String, List<String>>>();
		
		FileWriter fw;
		PrintWriter pw;
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			ScriptParser script = new ScriptParser(file);
			if(script.getConvoMap().size() > 0) {
				String scriptName = script.getFileName();
				for(Entry<Integer, Conversation> entry : script.getConvoMap().entrySet()) {
					Conversation convo = entry.getValue();
					Map<String, List<String>> scriptOccurrancesMap = null;
					List<String> blockList = null;
					
					for(int i = 0; i < convo.getBlockList().size(); i++) {
						ConvoSubBlockData block = convo.getBlockList().get(i);
						String text = block.getTextString();
						
						if(!stringMap.containsKey(text)) {
							scriptOccurrancesMap = new HashMap<String, List<String>>();
							stringMap.put(text, scriptOccurrancesMap);
						}
						else {
							scriptOccurrancesMap = stringMap.get(text);
						}
						
						if(!scriptOccurrancesMap.containsKey(scriptName)) {
							blockList = new ArrayList<String>();
							scriptOccurrancesMap.put(scriptName, blockList);
						}
						else {
							blockList = scriptOccurrancesMap.get(scriptName);
						}
						
						blockList.add(entry.getKey() + "." + i);
					}
				}
			}
		}
		
		try {
			fw = new FileWriter("matching.txt");
			pw = new PrintWriter(fw);
			
			for(Entry<String, Map<String, List<String>>> textMapEntry : stringMap.entrySet()) {
				boolean skipEntry = false;
				
				//if one script entry and block count == 1
				if(textMapEntry.getValue().size() == 1) {
					for(List<String> list : textMapEntry.getValue().values()) { //key doesn't matter; just interested in the list
						if(list.size() == 1) {
							skipEntry = true;
						}
					}
				}
				if(skipEntry) {
					continue;
				}
				
				pw.println("string: " + textMapEntry.getKey());
				
				for(Entry<String, List<String>> scriptMapEntry : textMapEntry.getValue().entrySet()) {
					pw.print(" - " + scriptMapEntry.getKey() + ": ");
					Iterator<String> iterator = scriptMapEntry.getValue().iterator();
					
					while(iterator.hasNext()) {
						pw.print(iterator.next());
						if(iterator.hasNext()) {
							pw.print(", ");
						}
					}
					pw.println();
				}
			}
			
			pw.close();
			fw.close();
		}
		catch(IOException i) {
			
		}
	}
	
	private void addGB(Component comp, int x, int y) {
		gbcon.gridx = x;
		gbcon.gridy = y;
		add(comp, gbcon);
	}
	
	public Mode getMode() {
		return mode;
	}
	
	public static class NoDeselectionModel extends DefaultListSelectionModel {
	    public void removeSelectionInterval(int index0, int index1) {
	    	//intentionally does nothing
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
	
	public static class N2dFileFilter extends FileFilter {
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
			
			if(extension.equals("n2d")) {
				return true;
			}
			return false;
		}

		public String getDescription() {
			return ".n2d";
		}	
	}
	
	public static class PNGFileFilter extends FileFilter {
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
			
			if(extension.equals("png")) {
				return true;
			}
			return false;
		}

		public String getDescription() {
			return ".png";
		}	
	}
}