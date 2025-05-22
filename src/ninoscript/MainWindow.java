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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
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
	private static final int MAXLINES = 6;
	private static final String ORIGINALSPEAKER = "Original speaker";
	private static final String NEWSPEAKER = "Modified speaker";
	private static final String ORIGINALANSWER = "Original answer";
	private static final String NEWANSWER = "Modified answer";
	
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
	
	JLabel sideLabel = new JLabel("");
	
	JList<ScriptParser> fileList = new JList<ScriptParser>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	JTextArea originalText = new JTextArea(6, 35);
	JList<Integer> originalTextLenLists = new JList<Integer>();
	JTextArea newText = new JTextArea(6, 35);
	JList<Integer> newTextLenLists = new JList<Integer>();
	JTextField originalExtraField = new JTextField(10);
	JTextField newExtraField = new JTextField(10);
	JCheckBox scriptingCheck = new JCheckBox("Hide extra text data");
	
	JCheckBox showUnusedCheck = new JCheckBox("Show unused convos");
	DefaultComboBoxModel<Integer> idComboModel = new DefaultComboBoxModel<Integer>();
	JComboBox<Integer> idCombo = new JComboBox<Integer>(idComboModel);
	
	ButtonGroup fontGroup = new ButtonGroup();
	JRadioButton f10Button = new JRadioButton("font10");
	JRadioButton f12Button = new JRadioButton("font12");
	
	LengthPanel originalLengths = new LengthPanel();
	LengthPanel newLengths = new LengthPanel();
	
	JPanel originalExtraPanel = new JPanel();
	JPanel newExtraPanel = new JPanel();
	
	JPanel regularTextPanel = new JPanel();
	JPanel multipleChoicePanel = new JPanel();
	
	private boolean isSpeakerBorder = true;
	private boolean updateComponents = true;
	
	private Map<String, Integer> currentFontMap = null;
	private Map<String, Integer> font10Map = null;
	private Map<String, Integer> font12Map = null;
	
	private Map<ScriptParser, File> scriptMap = new HashMap<ScriptParser, File>();
	private ScriptParser currentScript = null;
	private Conversation currentConvo;
	private ConvoSubBlockData currentBlock;
	private String currentString;
	private List<Integer> newLineLocs = new ArrayList<Integer>();
	
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
		
		originalText.setEditable(false);
		originalText.setOpaque(false);
		originalExtraField.setEditable(false);
		
		originalText.setMinimumSize(originalText.getPreferredSize());
		newText.setMinimumSize(newText.getPreferredSize());
		originalExtraField.setMinimumSize(originalExtraField.getPreferredSize());
		newExtraField.setMinimumSize(newExtraField.getPreferredSize());
		
		originalText.setFont(getFont());
		newText.setFont(getFont());
		
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
		
		JPanel originalTextPanel = new JPanel();
		originalTextPanel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		originalTextPanel.setBorder(BorderFactory.createTitledBorder("Original text"));
		//originalTextPanel.setBackground(Color.WHITE);

		c.gridy = 0;
		c.gridx = 0;
		originalTextPanel.add(originalText, c);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.NORTH;
		c.ipadx = 4;
		c.insets = new Insets(2, 0, 0, 0);
		originalTextPanel.add(originalLengths, c);
		
		JPanel newTextPanel = new JPanel();
		newTextPanel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		newTextPanel.setBorder(BorderFactory.createTitledBorder("Modified text"));
		
		c.gridy = 0;
		c.gridx = 0;
		newTextPanel.add(newText, c);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.NORTH;
		c.ipadx = 4;
		c.insets = new Insets(2, 0, 0, 0);
		newTextPanel.add(newLengths, c);
		
		originalExtraPanel.setBorder(BorderFactory.createTitledBorder(ORIGINALSPEAKER));
		originalExtraPanel.add(originalExtraField);
		
		newExtraPanel.setBorder(BorderFactory.createTitledBorder(NEWSPEAKER));
		newExtraPanel.add(newExtraField);
		
		c = new GridBagConstraints();
		regularTextPanel.setLayout(new GridBagLayout());
		c.anchor = GridBagConstraints.NORTHWEST;
		c.gridx = 0;
		c.gridy = 0;
		c.gridheight = 2;
		regularTextPanel.add(originalTextPanel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		regularTextPanel.add(newTextPanel, c);
		
		c.gridx = 1;
		c.gridy = 0;
		c.gridheight = 1;
		regularTextPanel.add(originalExtraPanel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		regularTextPanel.add(sideLabel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		regularTextPanel.add(newExtraPanel, c);
		
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
		addGB(showUnusedCheck, GridBagConstraints.RELATIVE, 0);
		
		addGB(blockPanel, 1, 1);
		addGB(buttonPanel, GridBagConstraints.RELATIVE, 1);
		
		addGB(scriptingCheck, GridBagConstraints.RELATIVE, 1);
		
		//widht 2
		gbcon.gridwidth = 3;
		addGB(regularTextPanel, 1, GridBagConstraints.RELATIVE);
		
		gbcon.gridwidth = 1;
		addGB(saveFileButton, 1, GridBagConstraints.RELATIVE);
		
		initListeners();
		originalText.setPreferredSize(getSize());
		newText.setPreferredSize(getSize());
		
		multipleChoicePanel.setVisible(false);
		multipleChoicePanel.setPreferredSize(regularTextPanel.getPreferredSize());
		
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
						//wipes any new stuff that wasn't saved to file
						block.setNewTextString(block.getTextString());
						if(block.hasExtraString()) {
							((ExtraStringConvoData) block).setNewExtraInfoString(((ExtraStringConvoData) block).getExtraInfoString());
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
			
			updateTextComponents();
			currentString = newText.getText();
			
			List<String> splits = Arrays.asList(currentBlock.getTextString().split("\n"));
			for(int i = 0; i < MAXLINES; i++) {
				if(i < splits.size()) {
					if(currentFontMap != null) {
						originalLengths.setLabelText(i, getPixelLength(splits.get(i)));
					}
					else {
						originalLengths.setLabelText(i, splits.get(i).length());
					}
				}
				else {
					originalLengths.setLabelText(i, -1);
				}
			}
			
			splitString();
		});
		
		newText.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
			}

			public void insertUpdate(DocumentEvent e) {
				update(e);
			}

			public void removeUpdate(DocumentEvent e) {
				update(e);
			}
			
			public void update(DocumentEvent e) {
				if(!updateComponents) {
					return;
				}
				
				Document doc = e.getDocument();
				EventType type = e.getType();
				int changeLength = e.getLength();
				
				int offset = e.getOffset();
				int lineChanged = 0;
				
				if(changeLength > 1) {
					splitString(); //if a large paste or delete happens, just resplit
					currentString = newText.getText();
					return;
				}
				
				for(int i = 0; i < MAXLINES; i++) {
					//int newLinePos = newLineLocs[i];
					if(offset > newLineLocs.get(i)) {
						continue;
					}
					
					lineChanged = i;
					break;
				}
			
				if(type == EventType.INSERT) {
					try {
						String newChar = doc.getText(offset, 1);
						
						if(newChar.equals("\n")) {
							splitString();
						}
						else {
							if(currentFontMap != null) {
								newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) + currentFontMap.get(newChar));
							}
							else {
								newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) + 1);
							}
							newLineLocs.set(lineChanged, newLineLocs.get(lineChanged) + 1);
						}
					}
					catch (BadLocationException b) {
					}
					currentString = newText.getText();
				}
				else if(type == EventType.REMOVE) {
					String removedChar = currentString.substring(offset, offset + 1);
					
					if(removedChar.equals("\n")) { //a newline was deleted
						splitString();
					}
					else {
						if(currentFontMap != null) {
							if(currentFontMap.containsKey(removedChar)) {
								newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) - currentFontMap.get(removedChar));
							}
						}
						else {
							newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) - 1);
						}
						newLineLocs.set(lineChanged, newLineLocs.get(lineChanged) - 1);
					}
					currentString = newText.getText();
				}
			}
		});
		
		scriptingCheck.addItemListener(event -> {
			updateTextComponents();
		});
		
		idCombo.addActionListener(event -> {
			if(!updateComponents) {
				return;
			}
			int index = (int) idCombo.getSelectedItem();
			
			if(currentScript != null && currentConvo != null) {
				for(ConvoSubBlockData block : currentConvo.getBlockList()) {
					//wipes any new stuff that wasn't saved to file
					block.setNewTextString(block.getTextString());
					if(block.hasExtraString()) {
						((ExtraStringConvoData) block).setNewExtraInfoString(((ExtraStringConvoData) block).getExtraInfoString());
					}
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
		
		showUnusedCheck.addItemListener(event -> {
			populateIDs();
		});
		
		saveFileButton.addActionListener(event -> {
			writeFile();
		});
		
		f10Button.addActionListener(event -> {
			currentFontMap = font10Map;
			splitString();
		});
		
		f12Button.addActionListener(event -> {
			currentFontMap = font12Map;
			splitString();
		});
	}
	
	private void populateIDs() {
		updateComponents = false;
		idComboModel.removeAllElements();
		if(showUnusedCheck.isSelected()) {
			idComboModel.addAll(currentScript.getConvoMap().keySet());
		}
		else {
			List<Integer> usedIDs = currentScript.getUsedConvoIDs();
			for(Entry<Integer, Conversation> entry : currentScript.getConvoMap().entrySet()) {
				if(usedIDs.contains(entry.getValue().getId())) {
					idComboModel.addElement(entry.getKey());
				}
			}
		}
		updateComponents = true;
		idCombo.setSelectedIndex(0);
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
	
	private void updateTextComponents() {
		ConvoMagic magic = currentBlock.getMagic();
		
		if(!scriptingCheck.isSelected()) {
			originalText.setText(currentBlock.getTextString());
		}
		else {
			String string = currentBlock.getTextString();
			String newString = null;
			int stringIndex = 0;
			
			while(stringIndex != -1) { //furigana first
				stringIndex = string.indexOf('<');
				int colonIndex = string.indexOf(':', stringIndex);
				
				if(stringIndex != -1) {
					newString = string.substring(stringIndex + 1, colonIndex) + string.substring(string.indexOf('>') + 1);
					if(stringIndex != 0) { //if first char isn't <, append the extra chars
						newString = string.substring(0, stringIndex) + newString;
					}
					string = newString;
				}
			}
			stringIndex = 0;
			while(stringIndex != -1) { //does opening and closing color tags separately
				stringIndex = string.indexOf('{'); //
				int closeBraceIndex = string.indexOf('}', stringIndex);
				if(stringIndex != -1) {
					newString = string.substring(closeBraceIndex + 1);
					
					if(stringIndex != 0) {
						newString = string.substring(0, stringIndex) + newString;
					}
					string = newString;
				}
			}
			originalText.setText(string);
		}
		
		newText.setText(currentBlock.getNewTextString());
		
		if(currentBlock.hasExtraString()) {
			ExtraStringConvoData extra = (ExtraStringConvoData) currentBlock;
			
			originalExtraField.setText(extra.getExtraInfoString());
			newExtraField.setText(extra.getNewExtraInfoString());
			newExtraField.setEnabled(true);
			
			if(extra.getSpeakerSide() != -1) {
				switch(extra.getSpeakerSide()) {
					case ExtraStringConvoData.NOSIDE:
						sideLabel.setText("NO SIDE");
						break;
					case ExtraStringConvoData.RIGHTSIDE:
						sideLabel.setText("RIGHT SIDE");
						break;
					case ExtraStringConvoData.LEFTSIDE:
						sideLabel.setText("LEFT SIDE");
						break;
				}
			}
			
			if(isSpeakerBorder && magic == ConvoMagic.TEXTENTRY) {
				originalExtraPanel.setBorder(BorderFactory.createTitledBorder(ORIGINALANSWER));
				newExtraPanel.setBorder(BorderFactory.createTitledBorder(NEWANSWER));
				isSpeakerBorder = false;
			}
			else {
				if(!isSpeakerBorder) {
					originalExtraPanel.setBorder(BorderFactory.createTitledBorder(ORIGINALSPEAKER));
					newExtraPanel.setBorder(BorderFactory.createTitledBorder(NEWSPEAKER));
					isSpeakerBorder = true;
				}
			}
			newExtraField.setEnabled(true);
		}
		else {
			originalExtraField.setText("");
			newExtraField.setText("");
			newExtraField.setEnabled(false);
			sideLabel.setText("");
		}
		
		if(magic == ConvoMagic.TEXTENTRY && !currentBlock.hasMainString()) {
			newText.setEnabled(false);
		}
		else {
			newText.setEnabled(true);
		}
	}
	
	private int getPixelLength(String string) {
		int pixelLen = 0;
		for(int i = 0; i < string.length(); i++) {
			String chara = string.substring(i, i+1);
			if(currentFontMap.containsKey(chara)) {
				pixelLen += currentFontMap.get(chara);
			}
		}
		return pixelLen;
	}
	
	//TODO: this sometimes acts up
	private void splitString() {
		List<String> splits = new ArrayList<String>();
		String text = newText.getText();
		int stringIndex = 0;
		int prevStringIndex = 0;
		int arrayIndex = 0;
		boolean isFirstLoop = true;
		
		newLineLocs.clear();
		
		while(stringIndex != -1) {
			if(!isFirstLoop) {
				stringIndex++;
				prevStringIndex = stringIndex;
			}
			stringIndex = text.indexOf('\n', stringIndex);
			String substring = null;
			
			if(stringIndex == -1) { //no more newlines
				if(prevStringIndex >= text.length()) {
					substring = "";
				}
				else {
					substring = text.substring(prevStringIndex);
				}
				newLineLocs.add(prevStringIndex + substring.length());
			}
			else {
				if(text.substring(prevStringIndex, stringIndex).equals("\n")) {
					System.out.println("newline");
					substring = "";
				}
				else {
					substring = text.substring(prevStringIndex, stringIndex);
				}
				newLineLocs.add(stringIndex);
			}
			
			if(currentFontMap != null) {
				newLengths.setLabelText(arrayIndex, getPixelLength(substring));
			}
			else {
				newLengths.setLabelText(arrayIndex, substring.length());
			}
			splits.add(substring);
			arrayIndex++;
			isFirstLoop = false;
		}
		while(arrayIndex < MAXLINES) {
			newLengths.setLabelText(arrayIndex, -1);
			newLineLocs.add(-1);
			arrayIndex++;
		}
	}
	
	private void clearComponents() {
		originalText.setText("");
		newText.setText("");
		originalExtraField.setText("");
		newExtraField.setText("");
		blockSpinner.setValue(0);
		blockMaxLabel.setText("of 0");
	}
	
	private void saveText() {
		if(currentBlock.getSharedStringList() != null) {
			for(ConvoSubBlockData block : currentBlock.getSharedStringList()) {
				block.setNewTextString(newText.getText());
			}
		}
		else {
			currentBlock.setNewTextString(newText.getText());
		}
		
		//for now keep speakers separate, but i think most of them are the same
		if(currentBlock.hasExtraString()) {
			((ExtraStringConvoData) currentBlock).setNewExtraInfoString(newExtraField.getText());
		}
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
			//setLayout(new BoxLayout(this,  BoxLayout.Y_AXIS));
			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			setOpaque(false);
			
			c.anchor = GridBagConstraints.BASELINE;
			
			c.gridx = 0;
			
			c.gridy = 0;
			add(lengthLabel1, c);
			
			c.gridy = 1;
			add(lengthLabel2, c);
			
			c.gridy = 2;
			add(lengthLabel3, c);
			
			c.gridy = 3;
			add(lengthLabel4, c);
			
			c.gridy = 4;
			add(lengthLabel5, c);
			
			c.gridy = 5;
			add(lengthLabel6, c);
			
		}
		
		public void setLabelText(int label, int content) {
			if(content == -1) {
				labels.get(label).setText("");
			}
			else if(label > 5) {
				return;
			}
			else {
				labels.get(label).setText(Integer.toString(content));
			}
		}
		
		public int getLabelText(int label) {
			return Integer.valueOf(labels.get(label).getText());
		}
	}
	
	public class MultipleChoicePanel extends JPanel {
		private JTextField originalText = new JTextField();
		private JTextField newText = new JTextField();
		
		public MultipleChoicePanel(String text) {
			originalText.setEditable(false);
			originalText.setText(text);
			
			originalText.setBorder(BorderFactory.createTitledBorder("Original text"));
			newText.setBorder(BorderFactory.createTitledBorder("Original text"));
			
			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			
			c.gridx = 0;
			c.gridy = 0;
			add(originalText, c);
			
			c.gridy = GridBagConstraints.RELATIVE;
			add(newText, c);
		}
		
		public String getNewText() {
			return newText.getText();
		}
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