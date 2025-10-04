package ninoscript;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;

import ninoscript.ConvoSubBlockData.MultipleChoiceConvoData;

@SuppressWarnings("serial")
public class MultipleChoicePanel extends DataPanel {
	private final int MAXOPTIONS = 3;
	
	private List<TextOptionPanel> subpanelList = new ArrayList<TextOptionPanel>();
	
	public MultipleChoicePanel() {
		FlowLayout layout = (FlowLayout) this.getLayout();
		layout.setAlignment(FlowLayout.LEFT);
		
		for(int i = 0; i < MAXOPTIONS; i++) {
			TextOptionPanel panel = new TextOptionPanel();
			subpanelList.add(panel);
			add(panel);
		}
	}
	
	public void loadStrings(ConvoSubBlockData currentBlock, Map<String, Integer> currentFontMap) {
		MultipleChoiceConvoData block = (MultipleChoiceConvoData) currentBlock;
		setCurrentFontMap(currentFontMap);
		
		for(TextOptionPanel panel : subpanelList) {
			panel.setOriginalText("");
			panel.setNewText("");
			panel.setTextEnabled(false);
		}
		
		List<String> originalStrings = block.getOriginalStrings();
		List<String> newStrings = block.getNewStrings();
		
		for(int i = 0; i < originalStrings.size(); i++) {
			TextOptionPanel panel = subpanelList.get(i);
			panel.setOriginalText(originalStrings.get(i));
			panel.setNewText(newStrings.get(i));
			panel.setTextEnabled(true);
		}
	}
	
	public void saveStrings(DataAdapter adapter) {
		List<String> newStrings = adapter.getNewStrings();
		
		for(int i = 0; i < newStrings.size(); i++) {
			newStrings.set(i, subpanelList.get(i).getNewText());
		}
	}
	
	public void clearComponents() {
		
	}
	
	public void loadOriginalString(DataAdapter adapter) {
		List<String> originalStrings = adapter.getOriginalStrings();
		if(originalStrings != null) {
			for(int i = 0; i < originalStrings.size(); i++) {
				TextOptionPanel panel = subpanelList.get(i);
				panel.setOriginalText(originalStrings.get(i));
			}
		}
	}

	public void removeStringFormatting(DataAdapter adapter) {
		for(TextOptionPanel panel : subpanelList) {
			if(!panel.getOriginalText().equals("")) {
				String string = panel.getOriginalText();
				string = stripFormatting(string);
				panel.setOriginalText(string);
			}
		}
	}
	
	public class TextOptionPanel extends JPanel {
		private JTextField originalText = new JTextField(15);
		private JTextField newText = new JTextField(15);
		
		public TextOptionPanel() {
			originalText.setEditable(false);
			
			JPanel originalPanel = new JPanel();
			originalPanel.add(originalText);	
			originalPanel.setBorder(BorderFactory.createTitledBorder("Original text"));
			
			JPanel newPanel = new JPanel();
			newPanel.add(newText);
			newPanel.setBorder(BorderFactory.createTitledBorder("New text"));
			
			setLayout(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			
			c.gridx = 0;
			c.gridy = 0;
			add(originalPanel, c);
			
			c.gridy = GridBagConstraints.RELATIVE;
			add(newPanel, c);
		}
		
		public void setOriginalText(String text) {
			originalText.setText(text);
		}
		
		public String getOriginalText() {
			return originalText.getText();
		}
		
		public void setNewText(String text) {
			newText.setText(text);
		}
		
		public String getNewText() {
			return newText.getText();
		}
		
		public void setTextEnabled(boolean enabled) {
			newText.setEnabled(enabled);
		}
	}

	@Override
	public void loadStrings(String oldString, String newString, Map<String, Integer> currentFontMap) {
		// TODO Auto-generated method stub
		
	}
}
