package ninoscript;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent.EventType;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import ninoscript.ConvoSubBlockData.ExtraStringConvoData;
import ninoscript.ScriptParser.ConvoMagic;

@SuppressWarnings("serial")
public class RegularTextPanel extends DataPanel {
	private static final int MAXLINES = 6;
	private static final String ORIGINALSPEAKER = "Original speaker";
	private static final String NEWSPEAKER = "Modified speaker";
	private static final String ORIGINALANSWER = "Original answer";
	private static final String NEWANSWER = "Modified answer";
	
	private boolean isSpeakerBorder = true;
	private List<Integer> newLineLocs = new ArrayList<Integer>();
	private String currentString = null;
	
	JTextArea originalText = new JTextArea(6, 35);
	JTextArea newText = new JTextArea(6, 35);
	
	JTextField originalExtraField = new JTextField(10);
	JTextField newExtraField = new JTextField(10);
	
	JLabel sideLabel = new JLabel("");
	
	JPanel originalExtraPanel = new JPanel();
	JPanel newExtraPanel = new JPanel();
	LengthPanel originalLengths = new LengthPanel();
	LengthPanel newLengths = new LengthPanel();
	
	public RegularTextPanel() {
		originalText.setEditable(false);
		originalText.setOpaque(false);
		originalExtraField.setEditable(false);
		
		originalText.setMinimumSize(originalText.getPreferredSize());
		newText.setMinimumSize(newText.getPreferredSize());
		originalExtraField.setMinimumSize(originalExtraField.getPreferredSize());
		newExtraField.setMinimumSize(newExtraField.getPreferredSize());
		
		originalText.setFont(getFont());
		newText.setFont(getFont());
		
		GridBagConstraints c = new GridBagConstraints();
		JPanel originalTextPanel = new JPanel();
		originalTextPanel.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		originalTextPanel.setBorder(BorderFactory.createTitledBorder("Original text"));

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
		setLayout(new GridBagLayout());
		c.anchor = GridBagConstraints.NORTHWEST;
		c.gridx = 0;
		c.gridy = 0;
		c.gridheight = 2;
		add(originalTextPanel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		c.gridheight = GridBagConstraints.REMAINDER;
		add(newTextPanel, c);
		
		c.gridx = 1;
		c.gridy = 0;
		c.gridheight = 1;
		add(originalExtraPanel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		add(sideLabel, c);
		c.gridy = GridBagConstraints.RELATIVE;
		add(newExtraPanel, c);
		
		originalText.setPreferredSize(getSize());
		newText.setPreferredSize(getSize());
	}
	
	public void initListeners() {
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
				//if(!updateComponents) {
				//	return;
				//}
				
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
	}

	public void loadStrings(ConvoSubBlockData currentBlock, Map<String, Integer> currentFontMap) {
		ConvoMagic magic = currentBlock.getMagic();
		setCurrentFontMap(currentFontMap);
		
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
			else {
				sideLabel.setText("");
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
	}

	public void saveStrings(ConvoSubBlockData currentBlock) {
		if(currentBlock.getSharedStringList() != null) { //multiple choice doesn't have as many shared strings
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
	
	public void loadOriginalString(ConvoSubBlockData currentBlock) {
		originalText.setText(currentBlock.getTextString());
	}
	
	public void removeStringFormatting(ConvoSubBlockData currentBlock) {
		String string = currentBlock.getTextString();
		string = stripFormatting(string);
		originalText.setText(string);
	}
	
	public void clearComponents() {
		originalText.setText("");
		newText.setText("");
		originalExtraField.setText("");
		newExtraField.setText("");
	}
	
	//TODO: this sometimes acts up
	public void splitString() {
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
	
	private class LengthPanel extends JPanel {
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
}
