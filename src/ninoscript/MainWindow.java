package ninoscript;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
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

import ninoscript.BlockData.*;
import ninoscript.ScriptReader.ConversationData;
import ninoscript.ScriptReader.Magic;

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
	
	DefaultListModel<ScriptReader> fileListModel = new DefaultListModel<ScriptReader>();
	SpinnerNumberModel blockSpinnerModel = new SpinnerNumberModel(0, 0, null, 1);
	JLabel blockMaxLabel = new JLabel("of 0");
	
	JList<ScriptReader> fileList = new JList<ScriptReader>(fileListModel);
	JSpinner blockSpinner = new JSpinner(blockSpinnerModel);
	JTextArea originalText = new JTextArea(6, 35);
	JList<Integer> originalTextLenLists = new JList<Integer>();
	JTextArea newText = new JTextArea(6, 35);
	JList<Integer> newTextLenLists = new JList<Integer>();
	JTextField originalExtraField = new JTextField(10);
	JTextField newExtraField = new JTextField(10);
	
	ButtonGroup fontGroup = new ButtonGroup();
	JRadioButton f10Button = new JRadioButton("font10");
	JRadioButton f12Button = new JRadioButton("font12");
	
	LengthPanel originalLengths = new LengthPanel();
	LengthPanel newLengths = new LengthPanel();
	
	JPanel originalExtraPanel = new JPanel();
	JPanel newExtraPanel = new JPanel();
	
	private boolean isSpeakerBorder = true;
	private boolean setText = true;
	
	private Map<String, Integer> currentFontMap = null;
	private Map<String, Integer> font10Map = null;
	private Map<String, Integer> font12Map = null;
	
	private Map<ScriptReader, File> scriptMap = new HashMap<ScriptReader, File>();
	private ScriptReader currentScript = null;
	private BlockData currentBlock;
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
		c.gridx = 1;
		c.ipadx = 10;
		blockPanel.add(blockSpinner, c);
		c.gridx = 2;
		c.ipadx = 5;
		blockPanel.add(blockMaxLabel, c);
		
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
		addGB(buttonPanel, 2, 0);
		
		gbcon.gridwidth = 2;
		addGB(originalTextPanel, 1, 1);
		addGB(newTextPanel, 1, 2);
		
		gbcon.gridwidth = 1;
		addGB(saveFileButton, 1, 3);
		
		gbcon.anchor = GridBagConstraints.NORTHWEST;
		addGB(originalExtraPanel, 3, 1);
		addGB(newExtraPanel, 3, 2);
		
		initListeners();
		originalText.setPreferredSize(getSize());
		newText.setPreferredSize(getSize());
		
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
		
		/*
		ActionListener loadFontListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
			}
		}; */
		
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
		
		fileList.addListSelectionListener(event -> {
			int index = fileList.getSelectedIndex();
			
			if(index != -1) {
				if(currentScript != null) {
					for(BlockData block : currentScript.getBlockList()) {
						//wipes any new stuff that wasn't saved to file
						block.setNewTextString(block.getTextString());
						if(block.hasExtraInfo()) {
							((ExtraInfoBlockData) block).setNewExtraInfoString(((ExtraInfoBlockData) block).getExtraInfoString());
						}
					}
				}
			
				//might need a is this file still here check
				currentScript = fileList.getSelectedValue();
				
				setText = false;
				blockSpinnerModel.setMaximum(currentScript.getBlockList().size() - 1);
				blockSpinnerModel.setMinimum(-1); //really roundabout way of always forcing the first block to be rendered
				blockSpinnerModel.setValue(-1);
				
				blockSpinnerModel.setValue(0);
				blockSpinnerModel.setMinimum(0);
				setText = true;
				blockMaxLabel.setText("of " + (currentScript.getBlockList().size() - 1));
			}
			else {
				clearComponents();
			}	
		});
		
		blockSpinner.addChangeListener(event -> {
			if((int) blockSpinner.getValue() == -1) {
				return;
			}
			if(setText) {
				saveText();
			}
			currentBlock = currentScript.getBlockList().get((int) blockSpinner.getValue());
			
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
			/*
			splits = Arrays.asList(currentString.split("\n"));
			i = 0;
			int loc = 0;
			while(i < splits.size()) {
				newLengths.setLabelText(i, splits.get(i).length());
				
				newLineLocs[i] = loc + splits.get(i).length() + 1;
				loc += newLineLocs[i];
				i++;
			}
			while(i < MAXLINES) {
				newLengths.setLabelText(i, -1);
				newLineLocs[i] = -1;
				i++;
			}
			*/
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
				if(!setText) {
					return;
				}
				
				Document doc = e.getDocument();
				EventType type = e.getType();
				int changeLength = e.getLength();
				
				int offset = e.getOffset();
				int lineChanged = 0;
				
				if(changeLength > 1) {
					splitString(); //if a large paste or delete happens, just resplit 
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
							newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) - currentFontMap.get(removedChar));
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
		
		saveFileButton.addActionListener(event -> {
			writeFile();
		});
		
		f10Button.addActionListener(event -> {
			currentFontMap = font10Map;
		});
		
		f12Button.addActionListener(event -> {
			currentFontMap = font12Map;
		});
	}
	
	private void updateTextComponents() {
		Magic magic = currentBlock.getMagic();
		
		originalText.setText(currentBlock.getTextString());
		newText.setText(currentBlock.getNewTextString());
		
		if(currentBlock.hasExtraInfo()) {
			originalExtraField.setText(((ExtraInfoBlockData) currentBlock).getExtraInfoString());
			newExtraField.setText(((ExtraInfoBlockData) currentBlock).getNewExtraInfoString());
			newExtraField.setEnabled(true);
			
			if(isSpeakerBorder && (magic == Magic.TEXTENTRY || magic == Magic.TEXTENTRYLONG ||
					magic == Magic.TEXTENTRYNODESCRIPT)) {
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
		}
		
		if(magic == Magic.TEXTENTRYNODESCRIPT) {
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
		int arrayIndex = 0;
		newLineLocs.clear();
		
		while(stringIndex != -1) {
			int prevIndex = stringIndex == 0 ? 0 : stringIndex + 1;
			stringIndex = prevIndex == 0 ? stringIndex : stringIndex + 1;
			stringIndex = text.indexOf('\n', stringIndex);
			String substring = null;
			
			if(stringIndex == -1) { //no more newlines
				if(prevIndex >= text.length()) {
					substring = "";
				}
				else {
					substring = text.substring(prevIndex);
				}
				//newLineLocs[arrayIndex] = prevIndex + substring.length();
				newLineLocs.add(prevIndex + substring.length());
			}
			else {
				if(text.substring(prevIndex, stringIndex).equals("\n")) {
					substring = "";
				}
				else {
					substring = text.substring(prevIndex, stringIndex);
				}
				//newLineLocs[arrayIndex] = stringIndex;
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
			for(BlockData block : currentBlock.getSharedStringList()) {
				block.setNewTextString(newText.getText());
			}
		}
		else {
			currentBlock.setNewTextString(newText.getText());
		}
		
		//for now keep speakers separate, but i think most of them are the same
		if(currentBlock.hasExtraInfo()) {
			((ExtraInfoBlockData) currentBlock).setNewExtraInfoString(newExtraField.getText());
		}
	}
	
	private void writeFile() {
		FileOutputStream fw = null;
		int originalFileIndex = 0;
		byte[] fullFileBytes = currentScript.getFullFileBytes();
		final int SPEAKERLENGTHOFFSET = -1; //one back from speaker start
		final byte[] CONVERSATIONMARKER = {0x0A, 0x09, 0x19, 0x00};
		int firstBlock = 0;
		
		saveText();
		
		try {
			fw = new FileOutputStream(scriptMap.get(currentScript));
		}
		catch (FileNotFoundException e) {
			return;
		}
		
		for(ConversationData convo : currentScript.getConvoList()) {
			int lastBlock = convo.getLastBlock();
			int newConvoSize = 0;
			int oldTotalBlocksSize = 0;
			int newTotalBlocksSize = 0;
			Map<BlockData, byte[]> blockMap = new HashMap<BlockData, byte[]>();
			Map<BlockData, byte[]> extraDataMap = new HashMap<BlockData, byte[]>();
			
			//first loop to get the new convo size
			for(int j = firstBlock; j < lastBlock + 1; j++) {
				BlockData block = currentScript.getBlockList().get(j);
				oldTotalBlocksSize += block.getFullBlockLength();
				int extraInfoSizeDiff = 0;
				byte[] newStringBytes = null;
				byte[] newExtraInfoBytes = null;
				try {
					newStringBytes = block.getNewTextString().getBytes("Shift-JIS");
					if(block.hasExtraInfo()) {
						newExtraInfoBytes = ((ExtraInfoBlockData) block).getNewExtraInfoString().getBytes("Shift-JIS");
						extraInfoSizeDiff = newExtraInfoBytes.length - ((ExtraInfoBlockData) block).getExtraInfoLength();
						extraDataMap.put(block, newExtraInfoBytes);
					}
				}
				catch (UnsupportedEncodingException e) {
				}
				
				newTotalBlocksSize += block.getFullBlockLength() + (newStringBytes.length - block.getTextLength()) + extraInfoSizeDiff;
				blockMap.put(block, newStringBytes);
			}
			newConvoSize = (convo.getLength() - oldTotalBlocksSize) + newTotalBlocksSize;
			
			try {
				//write everything up to the index of length of the convo start
				fw.write(fullFileBytes, originalFileIndex, convo.getStart() - originalFileIndex);
				
				//write gets upset if >255 and need to pad 00s anyway
				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				byte[] lengthBytes = bb.putInt(newConvoSize).array();
				
				fw.write(lengthBytes);				
				fw.write(CONVERSATIONMARKER);
				
				originalFileIndex += (convo.getStart() - originalFileIndex) + 8;
			}
			catch (IOException e) {
			}
			
			for(int j = firstBlock; j < lastBlock + 1; j++) {
				BlockData block = currentScript.getBlockList().get(j);
				byte[] stringBytes = blockMap.get(block);
				byte[] extraInfoBytes = null;
				int newBlockLength = 0;
				int extraInfoSizeDiff = 0;
				
				if(block.hasExtraInfo()) {
					extraInfoBytes = extraDataMap.get(block);
					extraInfoSizeDiff = extraInfoBytes.length - ((ExtraInfoBlockData) block).getExtraInfoLength();
				}
				newBlockLength = block.getFullBlockLength() + (stringBytes.length - block.getTextLength()) + extraInfoSizeDiff;
				
				try {
					//write everything up to this block
					fw.write(fullFileBytes, originalFileIndex, block.getBlockStart() - originalFileIndex);
					Magic magic = block.getMagic();
					byte[] info = magic.getFormat();
					
					byte[] lengthBytes = getShortBytes(newBlockLength);
					info[magic.getFullLengthOffset()] = lengthBytes[0];
					info[magic.getFullLengthOffset() + 1] = lengthBytes[1];
					
					//this writes the length of the chunk of data that comes first
					if(magic == Magic.DIALOGUE) {
						lengthBytes = getShortBytes(stringBytes.length);
						info[magic.getTextLengthOffset()] = lengthBytes[0];
						info[magic.getTextLengthOffset() + 1] = lengthBytes[1];
					}
					else if(magic == Magic.NONDIALOGUE) { //nondialogue can only be 1 byte length long
						info[magic.getTextLengthOffset()] = Integer.valueOf(stringBytes.length).byteValue();
					}
					else {
						lengthBytes = getShortBytes(extraInfoBytes.length);
						info[magic.getTextLengthOffset()] = lengthBytes[0];
						info[magic.getTextLengthOffset() + 1] = lengthBytes[1];
					}
					
					fw.write(info);
					originalFileIndex += (block.getBlockStart() - originalFileIndex) + info.length;
					
					//at this point, file should be at textStart
					if(magic == Magic.DIALOGUE || magic == Magic.NONDIALOGUE) {
						fw.write(stringBytes);
						originalFileIndex += block.getTextLength();
						
						if(block.hasExtraInfo()) {
							//speaker header
							fw.write(fullFileBytes, originalFileIndex, (((ExtraInfoBlockData) block).getExtraInfoStart() - originalFileIndex) + SPEAKERLENGTHOFFSET);	
							
							fw.write(extraInfoBytes.length);
							fw.write(extraInfoBytes);
							
							originalFileIndex += (((ExtraInfoBlockData) block).getExtraInfoStart() - originalFileIndex) + ((ExtraInfoBlockData) block).getExtraInfoLength();
						}
					}
					else if (magic != Magic.TEXTENTRYNODESCRIPT){
						fw.write(extraInfoBytes);
						originalFileIndex += ((ExtraInfoBlockData) block).getExtraInfoLength();
						lengthBytes = getShortBytes(stringBytes.length);
						fw.write(lengthBytes);
						fw.write(stringBytes);
						originalFileIndex += (block.getTextStart() - originalFileIndex) + block.getTextLength();
					}
				}
				catch (IOException e) {
					//
				}
			}
			firstBlock = convo.getLastBlock() + 1;
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
		
		reloadFile(scriptMap.get(currentScript));
	}
	
	private byte[] getShortBytes(int integer) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		return bb.putShort((short) integer).array();
	}
	
	private void loadFile(File file) { //dump old list, load one file
		ScriptReader script = new ScriptReader(file);
		
		fileListModel.clear();
		scriptMap.clear();
		scriptMap.put(script, file);
		fileListModel.addElement(script);
		fileList.clearSelection();
		fileList.setSelectedIndex(0);
	}
	
	private void loadFolder(File dir) {
		fileListModel.clear();
		scriptMap.clear();
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			ScriptReader script = new ScriptReader(file);
			if(script.getBlockList().size() > 0) {
				scriptMap.put(script, file);
				fileListModel.addElement(script);
			}
		}
		fileList.setSelectedIndex(0);
	}
	
	private void reloadFile(File file) {
		int index = fileListModel.indexOf(currentScript);
		ScriptReader script = new ScriptReader(file);
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
}