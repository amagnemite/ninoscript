package ninoscript;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
import javax.swing.filechooser.FileFilter;

import ninoscript.ScriptReader.BlockData;

@SuppressWarnings("serial")
public class MainWindow extends JFrame {
	private static final byte FULLWIDTHMARKER = Integer.valueOf(0x81).byteValue();
	private static final byte ELLIPSE = Integer.valueOf(0x63).byteValue();
	private static final byte OPENAPOSTROPHE = Integer.valueOf(0x67).byteValue();
	private static final byte CLOSEAPOSTROPHE = Integer.valueOf(0x68).byteValue();
	
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
	private BlockData currentBlock;
	
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
		originalSpeakerField.setEditable(false);
		
		originalText.setMinimumSize(originalText.getPreferredSize());
		newText.setMinimumSize(newText.getPreferredSize());
		originalSpeakerField.setMinimumSize(originalSpeakerField.getPreferredSize());
		newSpeakerField.setMinimumSize(newSpeakerField.getPreferredSize());
		
		originalText.setFont(getFont());
		newText.setFont(getFont());
		
		JPanel blockPanel = new JPanel();
		blockPanel.add(new JLabel("Block number:"));
		blockPanel.add(blockSpinner);
		blockPanel.add(blockMaxLabel);
		//blockSpinner.setPreferredSize(new Dimension(blockSpinner.getPreferredSize().width + 10, blockSpinner.getPreferredSize().height));
		
		/*
		JPanel originalTextPanel = new JPanel();
		originalTextPanel.setLayout(new BoxLayout(originalTextPanel, BoxLayout.X_AXIS));
		originalTextPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Original text"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		originalTextPanel.add(originalText);
		originalTextPanel.add(originalLengths);
		*/
		
		JPanel originalTextPanel = new JPanel();
		originalTextPanel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		//originalTextPanel.setBorder(BorderFactory.createCompoundBorder(
		//		BorderFactory.createTitledBorder("Original text"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
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
		//newTextPanel.setLayout(new BoxLayout(newTextPanel, BoxLayout.X_AXIS));
		//newTextPanel.setBorder(BorderFactory.createCompoundBorder(
		//		BorderFactory.createTitledBorder("Modified text"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		newTextPanel.setBorder(BorderFactory.createTitledBorder("Modified text"));
		//newTextPanel.add(newText);
		//newTextPanel.add(newLengths);
		
		c.gridy = 0;
		c.gridx = 0;
		newTextPanel.add(newText, c);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.NORTH;
		c.ipadx = 4;
		c.insets = new Insets(2, 0, 0, 0);
		newTextPanel.add(newLengths, c);
		
		JPanel originalSpeakerPanel = new JPanel();
		//originalSpeakerPanel.setBorder(BorderFactory.createCompoundBorder(
		//		BorderFactory.createTitledBorder("Original speaker"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		originalSpeakerPanel.setBorder(BorderFactory.createTitledBorder("Original speaker"));
		originalSpeakerPanel.add(originalSpeakerField);
		
		JPanel newSpeakerPanel = new JPanel();
		//newSpeakerPanel.setBorder(BorderFactory.createCompoundBorder(
		//		BorderFactory.createTitledBorder("Modified speaker"), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
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
			
		addGB(saveButton, 1, 3);
		
		gbcon.anchor = GridBagConstraints.NORTHWEST;
		addGB(originalSpeakerPanel, 2, 1);
		addGB(newSpeakerPanel, 2, 2);
		
		initListeners();
		originalText.setPreferredSize(getSize());
		newText.setPreferredSize(getSize());
		
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
				currentScript = fileList.getSelectedValue();
				//currentBlock = currentScript.getBlockList().get(0);
				
				blockSpinnerModel.setValue(0);
				blockSpinnerModel.setMaximum(currentScript.getBlockList().size() - 1);
				blockMaxLabel.setText("of " + (currentScript.getBlockList().size() - 1));
			}
			else {
				//clear components
			}	
		});
		
		blockSpinner.addChangeListener(event -> {
			if(currentScript == null || currentScript.getBlockList().size() == 0) {
				return;
				//clear components
			}
			
			currentBlock = currentScript.getBlockList().get((int) blockSpinner.getValue());
			byte[] text = currentBlock.getTextBytes();
			String unicodeText = new String();
			
			byte[] sorted = Arrays.copyOf(text, text.length);
			Arrays.sort(sorted);
			if(Arrays.binarySearch(sorted, FULLWIDTHMARKER) > -1) {
				//only iterate through the array if there's a fullwidth char
				int i = 0;
				byte[] buffer = new byte[text.length];
				int bufferIndex = 0;
				
				while(i < text.length) {
					byte b = text[i];
					if(b == FULLWIDTHMARKER) {
						byte nextChar = text[i+1];
						String equivalent = null;
						if(nextChar == ELLIPSE) {
							equivalent = "…";
						}
						else if(nextChar == OPENAPOSTROPHE || nextChar == CLOSEAPOSTROPHE) {
							equivalent = "\"";
						}
						
						unicodeText = unicodeText + new String(Arrays.copyOf(buffer, bufferIndex)) + equivalent;
						buffer = new byte[text.length];
						i += 2;
						bufferIndex = 0;
					}
					else {
						buffer[bufferIndex] = text[i];
						i++;
						bufferIndex++;
					}
				}
				
				if(bufferIndex > 0) {
					unicodeText = unicodeText + new String(Arrays.copyOf(buffer, bufferIndex));
				}
			}
			else {
				unicodeText = new String(text);
			}
			
			updateTextComponents(unicodeText);
		});
	}
	
	private void updateTextComponents(String text) {
		originalText.setText(text);
		newText.setText(text);
		
		if(currentBlock.getSpeakerBytes() != null) {
			String speaker = new String(currentBlock.getSpeakerBytes());
			originalSpeakerField.setText(speaker);
			newSpeakerField.setText(speaker);
		}
		else {
			originalSpeakerField.setText("");
			newSpeakerField.setText("");
		}
		
		String[] splits = text.split("\n");
		int i = 0;
		while(i < splits.length) {
			originalLengths.setLabelText(i, splits[i].length());
			newLengths.setLabelText(i, splits[i].length());
			i++;
		}
		while(i < 6) {
			originalLengths.setLabelText(i, -1);
			newLengths.setLabelText(i, -1);
			i++;
		}
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
		
		for(File file: dir.listFiles(new BinFileFilter())) {
			ScriptReader script = new ScriptReader(file);
			scriptMap.put(script, file);
			fileListModel.addElement(script);
		}
		fileList.setSelectedIndex(0);
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
			/*
			add(lengthLabel1);
			add(lengthLabel2);
			add(lengthLabel3);
			add(lengthLabel4);
			add(lengthLabel5);
			add(lengthLabel6);
			*/
			
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
		
		public void setLabelVisibility(int label, boolean isVisible) {
			labels.get(label).setVisible(isVisible);
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