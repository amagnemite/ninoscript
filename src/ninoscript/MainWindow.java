package ninoscript;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;


import n2dhandler.N2D;
import n2dhandler.TileMaker;
import ninoscript.ConvoSubBlockData.*;
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
	JMenuItem pngToBtx = new JMenuItem("Convert PNG to BTX");
	
	DefaultListModel<ScriptParser> fileListModel = new DefaultListModel<ScriptParser>();
	SpinnerNumberModel blockSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
	JLabel blockMaxLabel = new JLabel("of 0");
	
	JList<ScriptParser> fileList = new JList<ScriptParser>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	
	JList<Integer> originalTextLenLists = new JList<Integer>();
	
	JList<Integer> newTextLenLists = new JList<Integer>();
	JCheckBox scriptingCheck = new JCheckBox("Hide extra text data");
	
	DefaultComboBoxModel<String> idComboModel = new DefaultComboBoxModel<String>();
	JComboBox<String> convoCombo = new JComboBox<String>(idComboModel);
	
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
		idPanel.add(convoCombo, c);
		
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
		
		pngToBtx.addActionListener(event -> {
			JFileChooser c = new JFileChooser();
			c.setFileFilter(new PNGFileFilter());
			c.setDialogTitle("Select a PNG file");
			c.showOpenDialog(this);
			File png = c.getSelectedFile();
			
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
				if(currentScript != null) {
					for(Conversation convo : currentScript.getConvoMap().values()) {
						for(ConvoSubBlockData block : convo.getBlockList()) {
							block.resetNewStrings();
						}
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
		
		convoCombo.addActionListener(event -> {
			if(!updateComponents) {
				return;
			}
			int index = convoCombo.getSelectedIndex();
			
			/*
			if(currentScript != null && currentConvo != null) {
				for(ConvoSubBlockData block : currentConvo.getBlockList()) {
					block.resetNewStrings();
				}
			}
			*/
		
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
			convoCombo.setSelectedIndex(0);
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
		//byte length of the string length (byte/short)
		final int DIALOGUEBYTELENGTH = 2;
		final int NONDIALOGUEBYTELENGTH = 1;
		final int TEXTENTRYBYTELENGTH = 2;
		final int MULTIPLECHOICEBYTELENGTH = 2;
		final int SPEAKERBYTELENGTH = 1;
		final byte[] CONVERSATIONMARKER = {0x0A, 0x09, 0x19, 0x00};
		
		File originalFile = scriptMap.get(currentScript);
		File tempFile;
		File backupFile = new File(originalFile.getAbsolutePath() + ".bak");
		FileOutputStream fw;
		int originalFileIndex = 0;
		byte[] fullFileBytes = currentScript.getFullFileBytes();
		
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
		
			for(Conversation convo : currentScript.getConvoMap().values()) {
				ByteArrayOutputStream convoStream = new ByteArrayOutputStream();
				int convoHeaderExtraBytes = convo.getStartOffset() != 0 ? 8 : 4;
				int convoLength = convo.getLength();
				
				//write any non text containing convos, skip convo length and magic
				fw.write(fullFileBytes, originalFileIndex, convo.getStartOffset() - originalFileIndex);
				originalFileIndex += (convo.getStartOffset() + convoHeaderExtraBytes) - originalFileIndex;
				
				for(ConvoSubBlockData block : convo.getBlockList()) {
					byte[] mainStringArray;
					byte[] extraInfoArray = null;
					ByteArrayOutputStream blockStream = new ByteArrayOutputStream();
					ConvoMagic magic = block.getMagic();
					int length = DIALOGUEBYTELENGTH;
					int blockOffset = 0;
					int firstTextStart = magic == ConvoMagic.TEXTENTRY ? ((ExtraStringConvoData) block).getExtraInfoStartOffset()
							: block.getTextStartOffset();
					int blockLength = block.getOldFullBlockLength();
					
					//write any non text blocks that occur before this one
					convoStream.write(fullFileBytes, originalFileIndex, block.getStartOffset() - originalFileIndex);
					originalFileIndex += block.getStartOffset() - originalFileIndex;
						
					try {
						//combine new strings with block
						int blockPostLengthOffset = block.getStartOffset() + 3; //3 = id + two byte overall length
						blockStream.write(fullFileBytes, blockPostLengthOffset, firstTextStart - blockPostLengthOffset); //get bytes between start and string
						blockOffset = firstTextStart;
						
						switch(magic) {
							case NONDIALOGUE:
								length = NONDIALOGUEBYTELENGTH;
							case DIALOGUE:				
								mainStringArray = block.getNewTextString().getBytes("Shift-JIS");
								if(length == NONDIALOGUEBYTELENGTH) {
									blockStream.write(Integer.valueOf(mainStringArray.length).byteValue());
								}
								else {
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
								}
								blockStream.writeBytes(mainStringArray);
								blockOffset += block.getOldTextLength() + length;
								
								if(block.hasExtraString()) {
									extraInfoArray = ((ExtraStringConvoData) block).getNewExtraInfoString().getBytes("Shift-JIS");
									blockStream.write(fullFileBytes, blockOffset, ((ExtraStringConvoData) block).getExtraInfoStartOffset() - blockOffset); //everything in between main and speaker
									blockStream.write(Integer.valueOf(extraInfoArray.length).byteValue());
									blockStream.writeBytes(extraInfoArray);
									blockOffset = ((ExtraStringConvoData) block).getExtraInfoStartOffset() + ((ExtraStringConvoData) block).getOldExtraInfoLength() + SPEAKERBYTELENGTH;
								}
								break;
							case TEXTENTRY:	
								extraInfoArray = ((ExtraStringConvoData) block).getNewExtraInfoString().getBytes("Shift-JIS");
								blockStream.writeBytes(twoByteBuffer.putShort(0, (short) extraInfoArray.length).array());
								blockStream.writeBytes(extraInfoArray);
								blockOffset += ((ExtraStringConvoData) block).getOldExtraInfoLength() + TEXTENTRYBYTELENGTH;
								
								if(block.hasMainString()) { //text entry strings are end to end
									mainStringArray = block.getNewTextString().getBytes("Shift-JIS");
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
									blockStream.writeBytes(mainStringArray);
									blockOffset = block.getTextStartOffset() + block.getOldTextLength() + TEXTENTRYBYTELENGTH;
								}
								break;
							case MULTIPLECHOICE:
								int stringCount = 0;
								for(String string : ((MultipleChoiceConvoData) block).getNewStrings()) {
									mainStringArray = string.getBytes("Shift-JIS");
									blockStream.writeBytes(twoByteBuffer.putShort(0, (short) mainStringArray.length).array());
									blockStream.writeBytes(mainStringArray);
									stringCount++;
								}
								blockOffset = block.getTextStartOffset() + stringCount * MULTIPLECHOICEBYTELENGTH
										+ ((MultipleChoiceConvoData) block).getOriginalTotalStringsLength();
								break;
						}
						
					}
					catch (UnsupportedEncodingException e) {
					}
					
					int bytesRead = blockOffset - (block.getStartOffset() + 3);
					if(bytesRead < blockLength) {
						blockStream.write(fullFileBytes, blockOffset, blockLength - bytesRead);
					}
					
					//should have all the bytes except for magic and the total blockLength
					convoStream.write(block.getMagic().getValue());
					convoStream.writeBytes(twoByteBuffer.putShort(0, (short) blockStream.size()).array());
					blockStream.writeTo(convoStream);
					originalFileIndex += (blockLength + 3);
				}
				//the length of convo blocks does include the magic, unlike dialogue blocks
				int bytesRead = originalFileIndex - (convo.getStartOffset() + convoHeaderExtraBytes);
				if(bytesRead < convoLength) {
					convoStream.write(fullFileBytes, originalFileIndex, convoLength - bytesRead);
					originalFileIndex += convoLength - bytesRead;// + convoHeaderExtraBytes;
				}	
				
				//now should have everything but the convo magic and length in stream
				if(convo.getStartOffset() != 0) {
					fw.write(fourByteBuffer.putInt(0, convoStream.size()).array());
					fw.write(CONVERSATIONMARKER);
				}
				else { //for one convo files
					fw.write(CONVERSATIONMARKER);
				}
				convoStream.writeTo(fw);
			}
		
			//write anything else that's left
			if(originalFileIndex < fullFileBytes.length) {
				fw.write(fullFileBytes, originalFileIndex, fullFileBytes.length - originalFileIndex);
			}
			fw.close();
			if(backupFile.exists()) {
				backupFile.delete();
			}
			originalFile.renameTo(backupFile);
			tempFile.renameTo(originalFile);
		}
		catch (IOException e) {
		}
		
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