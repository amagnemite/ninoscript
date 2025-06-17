package ninoscript;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentEvent.EventType;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import n2dhandler.N2D;
import n2dhandler.TileMaker;
import ninoscript.ConvoSubBlockData.*;
import ninoscript.ScriptParser.Conversation;
import ninoscript.ScriptParser.ConvoMagic;

@SuppressWarnings("serial")
public class MainWindow extends JFrame {
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
	
	DefaultListModel<ScriptParser> fileListModel = new DefaultListModel<ScriptParser>();
	SpinnerNumberModel blockSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
	JLabel blockMaxLabel = new JLabel("of 0");
	
	JList<ScriptParser> fileList = new JList<ScriptParser>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	
	JList<Integer> originalTextLenLists = new JList<Integer>();
	
	JList<Integer> newTextLenLists = new JList<Integer>();
	JCheckBox scriptingCheck = new JCheckBox("Hide extra text data");
	
	DefaultComboBoxModel<String> idComboModel = new DefaultComboBoxModel<String>();
	JComboBox<String> idCombo = new JComboBox<String>(idComboModel);
	
	ButtonGroup fontGroup = new ButtonGroup();
	JRadioButton f10Button = new JRadioButton("font10");
	JRadioButton f12Button = new JRadioButton("font12");
	
	RegularTextPanel regularTextPanel = new RegularTextPanel();
	MultipleChoicePanel multipleChoicePanel = new MultipleChoicePanel();
	DataPanel currentPanel = regularTextPanel;
	
	private boolean updateComponents = true;
	
	private Map<String, Integer> currentFontMap = null;
	private Map<String, Integer> font10Map = null;
	private Map<String, Integer> font12Map = null;
	
	private Map<ScriptParser, File> scriptMap = new HashMap<ScriptParser, File>();
	private ScriptParser currentScript = null;
	private Conversation currentConvo;
	private ConvoSubBlockData currentBlock;
	
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
		
		JPanel idPanel = new JPanel();
		idPanel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.insets = new Insets(0, 5, 0, 5);
		c.gridy = 0;
		c.gridx = 0;
		idPanel.add(new JLabel("Convo number:"), c);
		c.insets = new Insets(0, 0, 0, 5);
		c.gridx = GridBagConstraints.RELATIVE;
		c.ipadx = 10;
		idPanel.add(idCombo, c);
		
		JScrollPane fileScroll = new JScrollPane(fileList);
		fileScroll.setMinimumSize(new Dimension(fileList.getPreferredScrollableViewportSize().width, 
				fileList.getPreferredScrollableViewportSize().height));
		
		gbcon.gridheight = 5;
		gbcon.fill = GridBagConstraints.BOTH;
		addGB(fileScroll, 0, 0);
		
		gbcon.fill = GridBagConstraints.NONE;
		gbcon.gridheight = 1;
		gbcon.anchor = GridBagConstraints.WEST;
		
		addGB(idPanel, GridBagConstraints.RELATIVE, 0);
		
		addGB(blockPanel, 1, 1);
		addGB(buttonPanel, GridBagConstraints.RELATIVE, 1);
		addGB(scriptingCheck, GridBagConstraints.RELATIVE, 1);
		
		gbcon.gridwidth = 3;
		addGB(regularTextPanel, 1, GridBagConstraints.RELATIVE);
		add(multipleChoicePanel, gbcon); //use the same constraints
		
		gbcon.gridwidth = 1;
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
			c.setFileFilter(new N2dFileFilter());
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
			
			
			c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			c.setFileFilter(null);
			c.setMultiSelectionEnabled(false);
			c.showSaveDialog(this);
			File saveDir = c.getSelectedFile();
			
			if(saveDir == null) {
				saveDir = targetFiles[0].getParentFile();
			}
			TileMaker.makeTiles(targetFiles, saveDir);
		});
		
		fileList.addListSelectionListener(event -> {
			int index = fileList.getSelectedIndex();
			
			if(index != -1) {
				if(currentScript != null) {
					for(ConvoSubBlockData block : currentConvo.getBlockList()) {
						block.resetNewStrings();
					}
				}
			
				//might need a is this file still here check
				currentScript = fileList.getSelectedValue();
				populateIDs();
				
				updateComponents = false;
				blockSpinnerModel.setMinimum(-1); //really roundabout way of always forcing the first block to be rendered
				blockSpinnerModel.setValue(-1);
				blockSpinnerModel.setMaximum(currentConvo.getBlockList().size() - 1);
				blockSpinnerModel.setMinimum(0);
				updateComponents = true;
				blockSpinnerModel.setValue(0);
				
				blockMaxLabel.setText("of " + (currentConvo.getBlockList().size() - 1));
			}
			else {
				clearComponents();
			}	
		});
		
		blockSpinner.addChangeListener(event -> {
			if(!updateComponents) {
				return;
			}
			if(currentBlock != null) {
				saveText();
			}
			
			currentBlock = currentConvo.getBlockList().get((int) blockSpinner.getValue());
			
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
			setScriptingState();
		});
		
		scriptingCheck.addItemListener(event -> {
			setScriptingState();
		});
		
		idCombo.addActionListener(event -> {
			if(!updateComponents) {
				return;
			}
			int index = idCombo.getSelectedIndex();
			
			if(currentScript != null && currentConvo != null) {
				for(ConvoSubBlockData block : currentConvo.getBlockList()) {
					block.resetNewStrings();
				}
			}
		
			currentConvo = currentScript.getConvoMap().get(index);
			System.out.println(currentConvo.getId() + " " + Integer.toHexString(currentConvo.getId()));
			
			updateComponents = false;
			blockSpinnerModel.setMinimum(-1); //really roundabout way of always forcing the first block to be rendered
			blockSpinnerModel.setValue(-1);
			blockSpinnerModel.setMaximum(currentConvo.getBlockList().size() - 1);
			blockSpinnerModel.setMinimum(0);
			updateComponents = true;
			blockSpinnerModel.setValue(0);
			
			blockMaxLabel.setText("of " + (currentConvo.getBlockList().size() - 1));
		});
		
		saveFileButton.addActionListener(event -> {
			writeFile();
		});
		
		f10Button.addActionListener(event -> {
			currentFontMap = font10Map;
			currentPanel.setCurrentFontMap(currentFontMap);
			if(currentPanel == regularTextPanel) {
				regularTextPanel.splitString();
			}
		});
		
		f12Button.addActionListener(event -> {
			currentFontMap = font12Map;
			currentPanel.setCurrentFontMap(currentFontMap);
			if(currentPanel == regularTextPanel) {
				regularTextPanel.splitString();
			}
		});
	}
	
	private void populateIDs() {
		updateComponents = false;
		idComboModel.removeAllElements();
		List<Integer> usedIDs = currentScript.getUsedConvoIDs();
		
		for(Entry<Integer, Conversation> entry : currentScript.getConvoMap().entrySet()) {
			String key = entry.getKey().toString();
			if(usedIDs.contains(entry.getValue().getId())) {
				idComboModel.addElement(key);
			}
			else {
				idComboModel.addElement(key + " (UNUSED)");
			}
		}

		updateComponents = true;
		if(idComboModel.getSize() > 0) {
			idCombo.setSelectedIndex(0);
		}
	}
	
	private void findMatchingFiles(File parentDir, File saveDir, String filter) {
		File[] files = parentDir.listFiles((filepath) -> {
			if(filepath.isDirectory()) {
				return true;
			}
			else {
				if(filepath.getName().contains(filter) && filepath.getName().contains(".n2d")) {
					return true;
				}
				else {
					return false;
				}
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
			currentPanel.loadOriginalString(currentBlock);
		}
		else {
			currentPanel.removeStringFormatting(currentBlock);
		}
	}
	
	private void clearComponents() {
		currentPanel.clearComponents();
		blockSpinner.setValue(0);
		blockMaxLabel.setText("of 0");
	}
	
	private void saveText() {
		currentPanel.saveStrings(currentBlock);
	}
	
	private void writeFile() {
		File originalFile = scriptMap.get(currentScript);
		File tempFile;
		File backupFile = new File(originalFile.getAbsolutePath() + ".bak");
		FileOutputStream fw;
		int originalFileIndex = 0;
		byte[] fullFileBytes = currentScript.getFullFileBytes();
		final byte[] CONVERSATIONMARKER = {0x0A, 0x09, 0x19, 0x00};
		
		//write gets upset if >255 and need to pad 00s anyway
		ByteBuffer fourByteBuffer = ByteBuffer.allocate(4);
		fourByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		ByteBuffer twoByteBuffer = ByteBuffer.allocate(2);
		twoByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		saveText();
		
		try {
			tempFile = File.createTempFile(originalFile.getName(), ".tmp", originalFile.getParentFile());
			tempFile.deleteOnExit();
			fw = new FileOutputStream(tempFile);
		}
		catch (IOException i) {
			return;
		}
		
		for(Conversation convo : currentScript.getConvoMap().values()) {
			int newConvoSize = 0;
			int oldTotalBlocksSize = 0;
			int newTotalBlocksSize = 0;
			Map<ConvoSubBlockData, byte[]> blockMap = new HashMap<ConvoSubBlockData, byte[]>();
			Map<ConvoSubBlockData, byte[]> extraDataMap = new HashMap<ConvoSubBlockData, byte[]>();
			
			//first loop to get the new overall convo size
			for(ConvoSubBlockData block : convo.getBlockList()) {
				oldTotalBlocksSize += block.getOldFullBlockLength();
				int extraInfoSizeDiff = 0;
				byte[] newStringBytes = null;
				byte[] newExtraInfoBytes = null;
				try {
					newStringBytes = block.getNewTextString().getBytes("Shift-JIS");
					if(block.hasExtraString()) {
						newExtraInfoBytes = ((ExtraStringConvoData) block).getNewExtraInfoString().getBytes("Shift-JIS");
						extraInfoSizeDiff = newExtraInfoBytes.length - ((ExtraStringConvoData) block).getOldExtraInfoLength();
						extraDataMap.put(block, newExtraInfoBytes);
					}
				}
				catch (UnsupportedEncodingException e) {
				}
				
				newTotalBlocksSize += block.getOldFullBlockLength() + (newStringBytes.length - block.getOldTextLength()) + extraInfoSizeDiff;
				blockMap.put(block, newStringBytes);
			}
			newConvoSize = (convo.getLength() - oldTotalBlocksSize) + newTotalBlocksSize;
			
			//write everything up to the index of length of the convo start
			try {	
				if(convo.getStartOffset() != 0) {
					fw.write(fullFileBytes, originalFileIndex, convo.getStartOffset() - originalFileIndex);
					
					fw.write(fourByteBuffer.putInt(0, newConvoSize).array());
					fw.write(CONVERSATIONMARKER);
					
					originalFileIndex += (convo.getStartOffset() - originalFileIndex) + 8;
				}
				else { //for one convo files
					fw.write(CONVERSATIONMARKER);
					originalFileIndex += 4;
				}
			}
			catch (IOException e) {
			}
			
			//now writing actual convo blocks
			for(ConvoSubBlockData block : convo.getBlockList()) {
				byte[] stringBytes = blockMap.get(block);
				byte[] extraInfoBytes = null;
				int newBlockLength = 0;
				int extraInfoSizeDiff = 0;
				
				if(block.hasExtraString()) {
					extraInfoBytes = extraDataMap.get(block);
					extraInfoSizeDiff = extraInfoBytes.length - ((ExtraStringConvoData) block).getOldExtraInfoLength();
				}
				newBlockLength = block.getOldFullBlockLength() + (stringBytes.length - block.getOldTextLength()) + extraInfoSizeDiff;
				
				try {
					//write everything up to this block
					fw.write(fullFileBytes, originalFileIndex, block.getBlockStart() - originalFileIndex);
					
					originalFileIndex += (block.getBlockStart() - originalFileIndex);
					
					ConvoMagic magic = block.getMagic();
					fw.write(block.getMagic().getValue());
					fw.write(twoByteBuffer.putShort(0, (short) newBlockLength).array()); //new overall block length
					
					originalFileIndex += 3;
					
					//write everything between the full length and the text length
					fw.write(fullFileBytes, originalFileIndex, block.getTextStart() - originalFileIndex);
					
					originalFileIndex += block.getTextStart() - originalFileIndex;
					
					//text length
					switch(magic) {
						case DIALOGUE:
							fw.write(twoByteBuffer.putShort(0, (short) stringBytes.length).array()); //new length
							originalFileIndex += 2;
							break;			
						case NONDIALOGUE: //nondialogue can only be 1 byte length long
							fw.write(Integer.valueOf(stringBytes.length).byteValue());
							originalFileIndex++;
							break;
						case TEXTENTRY:
							fw.write(twoByteBuffer.putShort(0, (short) extraInfoBytes.length).array()); //new length
							originalFileIndex += 2;
							break;
						case MULTIPLECHOICE:
							
							break;
					}
					
					//at this point, file should be at the start of the actual text
					switch(magic) {
						case DIALOGUE:
						case NONDIALOGUE:
							fw.write(stringBytes);
							originalFileIndex += block.getOldTextLength();
							if(block.hasExtraString()) {
								fw.write(fullFileBytes, originalFileIndex, ((ExtraStringConvoData) block).getExtraInfoStart() - originalFileIndex);	
								
								fw.write(extraInfoBytes.length);
								fw.write(extraInfoBytes);
								
								originalFileIndex += (((ExtraStringConvoData) block).getExtraInfoStart() - originalFileIndex) + 1 + ((ExtraStringConvoData) block).getOldExtraInfoLength();
							}
							break;
						case TEXTENTRY:
							fw.write(extraInfoBytes);
							originalFileIndex += ((ExtraStringConvoData) block).getOldExtraInfoLength();
							if(block.hasMainString()) { //text entry strings are placed end to end
								fw.write(twoByteBuffer.putShort(0, (short) stringBytes.length).array());
								fw.write(stringBytes);
								originalFileIndex += (block.getTextStart() - originalFileIndex) + 2 + block.getOldTextLength();
							}
							break;
						case MULTIPLECHOICE:
							break;
					}
				}
				catch (IOException e) {
					//
				}
			}
		}
		
		try {
			if(originalFileIndex < fullFileBytes.length) {
				fw.write(fullFileBytes, originalFileIndex, fullFileBytes.length - originalFileIndex);
			}
			fw.close();
		}
		catch (IOException e) {
			//
		}
		
		if(backupFile.exists()) {
			backupFile.delete();
		}
		originalFile.renameTo(backupFile);
		tempFile.renameTo(originalFile);
		
		reloadFile(scriptMap.get(currentScript));
	}
	
	private void loadFile(File file) { //dump old list, load one file
		ScriptParser script = new ScriptParser(file);
		
		fileListModel.clear();
		scriptMap.clear();
		scriptMap.put(script, file);
		fileListModel.addElement(script);
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
	
	private void loadFolder(File dir) {
		fileListModel.clear();
		scriptMap.clear();
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			ScriptParser script = new ScriptParser(file);
			if(script.getConvoMap().size() > 0) {
				scriptMap.put(script, file);
				fileListModel.addElement(script);
				
			}
		}
		fileList.setSelectedIndex(0);
	}
	
	private void reloadFile(File file) {
		int index = fileListModel.indexOf(currentScript);
		ScriptParser script = new ScriptParser(file);
		scriptMap.remove(currentScript);
		scriptMap.put(script, file);
		fileListModel.set(index, script);
		fileList.clearSelection();
		fileList.setSelectedIndex(index);
	}
	
	private void addGB(Component comp, int x, int y) {
		gbcon.gridx = x;
		gbcon.gridy = y;
		add(comp, gbcon);
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