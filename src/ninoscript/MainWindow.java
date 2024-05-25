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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
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

import ninoscript.ScriptReader.BlockData;
import ninoscript.ScriptReader.ConversationData;
import ninoscript.ScriptReader.Magic;

@SuppressWarnings("serial")
public class MainWindow extends JFrame {
	private static final int MAXLINES = 6;
	
	JMenuBar menuBar = new JMenuBar();
	JMenu optionsMenu = new JMenu("Options");
	JMenuItem loadFiles = new JMenuItem("Load");
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
	JTextField originalSpeakerField = new JTextField(10);
	JTextField newSpeakerField = new JTextField(10);
	
	LengthPanel originalLengths = new LengthPanel();
	LengthPanel newLengths = new LengthPanel();
	
	private boolean setText = true;
	
	private Map<ScriptReader, File> scriptMap = new HashMap<ScriptReader, File>();
	private ScriptReader currentScript = null;
	private BlockData currentBlock;
	private String currentString;
	//private List<String> substringList = new ArrayList<String>();
	private int[] newLineLocs = {-1, -1, -1, -1, -1, -1};
	
	private GridBagConstraints gbcon = new GridBagConstraints();
	
	public MainWindow() {
		super("script edit");
		setLayout(new GridBagLayout());
		setSize(700, 400);
		
		optionsMenu.add(loadFiles);
		menuBar.add(optionsMenu);
		setJMenuBar(menuBar);
		
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setSelectionModel(new NoDeselectionModel());
		
		originalText.setEditable(false);
		originalText.setOpaque(false);
		originalSpeakerField.setEditable(false);
		
		originalText.setMinimumSize(originalText.getPreferredSize());
		newText.setMinimumSize(newText.getPreferredSize());
		originalSpeakerField.setMinimumSize(originalSpeakerField.getPreferredSize());
		newSpeakerField.setMinimumSize(newSpeakerField.getPreferredSize());
		
		originalText.setFont(getFont());
		newText.setFont(getFont());
		
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
		
		JPanel originalSpeakerPanel = new JPanel();
		originalSpeakerPanel.setBorder(BorderFactory.createTitledBorder("Original speaker"));
		originalSpeakerPanel.add(originalSpeakerField);
		
		JPanel newSpeakerPanel = new JPanel();
		newSpeakerPanel.setBorder(BorderFactory.createTitledBorder("Modified speaker"));
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
			
		addGB(saveFileButton, 1, 3);
		
		gbcon.anchor = GridBagConstraints.NORTHWEST;
		addGB(originalSpeakerPanel, 2, 1);
		addGB(newSpeakerPanel, 2, 2);
		
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
		
		fileList.addListSelectionListener(event -> {
			int index = fileList.getSelectedIndex();
			
			if(index != -1) {
				if(currentScript != null) {
					for(BlockData block : currentScript.getBlockList()) {
						//wipes any new stuff that wasn't saved to file
						block.setNewTextString(block.getTextString());
						block.setNewSpeakerString(block.getSpeakerString());
					}
				}
			
				currentScript = fileList.getSelectedValue();
				
				/*
				textLengths.clear();
				speakerLengths.clear();
				for(BlockData data : currentScript.getBlockList()) {
					textLengths.add(data.getTextLength());
					speakerLengths.add(data.getSpeakerLength());
				}	
				*/
				
				setText = false;
				blockSpinnerModel.setValue(0);
				blockSpinnerModel.setMaximum(currentScript.getBlockList().size() - 1);
				setText = true;
				blockMaxLabel.setText("of " + (currentScript.getBlockList().size() - 1));
			}
			else {
				clearComponents();
			}	
		});
		
		blockSpinner.addChangeListener(event -> {
			if(setText) {
				saveText();
			}
			currentBlock = currentScript.getBlockList().get((int) blockSpinner.getValue());
			
			updateTextComponents();
			currentString = newText.getText();
			
			String[] splits = currentBlock.getTextString().split("\n");
			int i = 0;
			while(i < MAXLINES) {
				if(i < splits.length) {
					originalLengths.setLabelText(i, splits[i].length());
				}
				else {
					originalLengths.setLabelText(i, -1);
				}
				i++;
			}
			
			splits = currentString.split("\n");
			i = 0;
			int loc = 0;
			while(i < splits.length) {
				newLengths.setLabelText(i, splits[i].length());
				
				newLineLocs[i] = loc + splits[i].length() + 1;
				loc += newLineLocs[i];
				i++;
			}
			while(i < 6) {
				newLengths.setLabelText(i, -1);
				newLineLocs[i] = -1;
				i++;
			}
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
				
				//System.out.println(type + " length " + changeLength + " offset " + offset);
				if(changeLength > 1) {
					//if a large paste or delete happens, just resplit 
					splitString();
					return;
				}
				
				for(int i = 0; i < 6; i++) {
					//int newLinePos = newLineLocs[i];
					if(offset > newLineLocs[i]) {
						continue;
					}
					
					lineChanged = i;
					break;
				}
			
				if(type == EventType.INSERT) {
					try {
						if(doc.getText(offset, 1).equals("\n")) {
							splitString();
						}
						else {
							//System.out.println(lineChanged);
							//System.out.println(newLineLocs[lineChanged]);
							newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) + 1);
							newLineLocs[lineChanged]++;
						}
					}
					catch (BadLocationException b) {
							
					}
					currentString = newText.getText();
				}
				else if(type == EventType.REMOVE) {
					//System.out.println("removed " + currentString.substring(offset, offset + 1).getBytes()[0]);
					if(currentString.substring(offset, offset + 1).getBytes()[0] == 0x0A) { //a newline was deleted
						splitString();
					}
					else {
						newLengths.setLabelText(lineChanged, newLengths.getLabelText(lineChanged) - 1);
						newLineLocs[lineChanged]--;
					}
					currentString = newText.getText();
				}
			}
		});
		
		saveFileButton.addActionListener(event -> {
			writeFile();
		});
	}
	
	private void updateTextComponents() {
		originalText.setText(currentBlock.getTextString());
		newText.setText(currentBlock.getNewTextString());
		
		if(currentBlock.getSpeakerString() != null) {
			originalSpeakerField.setText(currentBlock.getSpeakerString());
			newSpeakerField.setText(currentBlock.getNewSpeakerString());
			newSpeakerField.setEnabled(true);
		}
		else {
			originalSpeakerField.setText("");
			newSpeakerField.setText("");
			if(currentBlock.getMagic() == Magic.NONDIALOGUE) {
				newSpeakerField.setEnabled(false);
			}
			else {
				newSpeakerField.setEnabled(true);
			}
		}
	}
	
	private void splitString() {
		String[] splits = new String[6];
		String text = newText.getText();
		int i = 0;
		int index = 0;
		
		while(i != -1) {
			int oldI = i;
			i = oldI != 0 ? i + 1 : i;
			i = text.indexOf('\n', i);
			
			if(i == -1) { //no more newlines
				if(oldI + 1 >= text.length()) {
					splits[index] = "";
				}
				else {
					splits[index] = text.substring(oldI + 1);
				}
				newLineLocs[index] = oldI + 1 + splits[index].length();
			}
			else {
				if(text.substring(oldI, i).equals("\n")) {
					splits[index] = "";
				}
				else {
					splits[index] = text.substring(oldI, i);
				}
				newLineLocs[index] = i;
			}
			
			newLengths.setLabelText(index, splits[index].length());
			index++;
		}
		while(index < 6) {
			newLengths.setLabelText(index, -1);
			newLineLocs[index] = -1;
			index++;
		}
	}
	
	private void clearComponents() {
		originalText.setText("");
		newText.setText("");
		originalSpeakerField.setText("");
		newSpeakerField.setText("");
		blockSpinner.setValue(0);
		blockMaxLabel.setText("of 0");
	}
	
	private void saveText() {
		currentBlock.setNewTextString(newText.getText());
		if(currentBlock.hasSpeaker()) {
			currentBlock.setNewSpeakerString(newSpeakerField.getText());
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
			Map<BlockData, byte[]> speakerMap = new HashMap<BlockData, byte[]>();
			
			//first loop to get the new convo size
			for(int j = firstBlock; j < lastBlock + 1; j++) {
				BlockData block = currentScript.getBlockList().get(j);
				oldTotalBlocksSize += block.getFullBlockLength();
				int speakerSizeDiff = 0;
				byte[] newStringBytes = null;
				byte[] newSpeakerBytes = null;
				try {
					newStringBytes = block.getNewTextString().getBytes("Shift-JIS");
					newSpeakerBytes = block.getSpeakerString() != null ? block.getNewSpeakerString().getBytes("Shift-JIS") : null;
				}
				catch (UnsupportedEncodingException e) {
				}
				
				if(block.hasSpeaker()) {
					speakerSizeDiff = newSpeakerBytes.length - block.getSpeakerLength();
					speakerMap.put(block, newSpeakerBytes);
				}
				
				newTotalBlocksSize += block.getFullBlockLength() + (newStringBytes.length - block.getTextLength()) + speakerSizeDiff;
				blockMap.put(block, newStringBytes);
			}
			newConvoSize = (convo.getLength() - oldTotalBlocksSize) + newTotalBlocksSize;
			
			try {
				//write everything up to the index of length of the convo start
				fw.write(fullFileBytes, originalFileIndex, convo.getStart() - originalFileIndex);
				
				//write gets upset if >255 and need to pad 00s anyway
				byte[] lengthBytes = ByteBuffer.allocate(4).putInt(newConvoSize).array();
				fw.write(lengthBytes[3]);
				fw.write(lengthBytes[2]);
				fw.write(lengthBytes[1]);
				fw.write(lengthBytes[0]);
				
				fw.write(CONVERSATIONMARKER);
				
				originalFileIndex += (convo.getStart() - originalFileIndex) + 8;
			}
			catch (IOException e) {
			}
			
			for(int j = firstBlock; j < lastBlock + 1; j++) {
				BlockData block = currentScript.getBlockList().get(j);
				byte[] stringBytes = blockMap.get(block);
				byte[] newSpeakerBytes = block.getSpeakerString() != null ? speakerMap.get(block) : null;
				int newBlockLength = 0;
				int speakerSizeDiff = 0;
				
				if(block.hasSpeaker()) {
					speakerSizeDiff = newSpeakerBytes.length - block.getSpeakerLength();
				}
				newBlockLength = block.getFullBlockLength() + (stringBytes.length - block.getTextLength()) + speakerSizeDiff;
				
				try {
					//write everything up to this block
					fw.write(fullFileBytes, originalFileIndex, block.getBlockStart() - originalFileIndex);
					int bytes = 0;
					
					//write the rest of the block + text header
					fw.write(block.getMagic().getValue());
					fw.write(newBlockLength);
					fw.write(0x00);
					fw.write(0x01);
					fw.write(stringBytes.length);
					
					if(block.getMagic() == Magic.DIALOGUE) {
						fw.write(0x00);
						bytes = 6;
					}
					else {
						bytes = 5;
					}		
				
					originalFileIndex += (block.getBlockStart() - originalFileIndex) + bytes;
					
					//at this point, file should be at textStart
					fw.write(stringBytes);
					originalFileIndex += block.getTextLength();
					
					if(block.hasSpeaker()) {
						//speaker header
						fw.write(fullFileBytes, originalFileIndex, (block.getSpeakerStart() - originalFileIndex) + SPEAKERLENGTHOFFSET);	
						
						fw.write(newSpeakerBytes.length);
						fw.write(newSpeakerBytes);
						
						originalFileIndex += (block.getSpeakerStart() - originalFileIndex) + block.getSpeakerLength();
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