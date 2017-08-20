package awesome.console.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

@State(
		name = "Awesome Console Config",
		storages = {
				@Storage(id = "IDE config dir", file = StoragePathMacros.APP_CONFIG + "/awesomeconsoleconfig.xml")
		}
)
public class AwesomeConsoleConfig implements PersistentStateComponent<AwesomeConsoleConfig>, Configurable, ApplicationComponent {
	public boolean SPLIT_ON_LIMIT = false;
	public boolean LIMIT_LINE_LENGTH = true;
	public int LINE_MAX_LENGTH = 1024;
	public boolean SEARCH_URLS = true;
	public boolean SEARCH_FILES = true;
	public String[] PROJECT_SOURCE_PATHS = "".split(" ");
	public String[] EXTENSIONS_TO_ALWAYS_MATCH = "py js ts".split(" ");
	public boolean RELAXED_SOURCE_MATCHING = true;
    private String extensionsToAlwaysMatch;
    private String projectSourcePaths;

    public boolean alwaysMatchExtension(final String extension) {
        return Arrays.asList(EXTENSIONS_TO_ALWAYS_MATCH).contains(extension);
//        for (String s : EXTENSIONS_TO_ALWAYS_MATCH) {
//            if (s.equals(extension)) return true;
//        }
//        return false;
    }

	@Transient
	private AwesomeConsoleConfigForm form;

	/**
	 * PersistentStateComponent
	 */
	@Nullable
	@Override
	public AwesomeConsoleConfig getState() {
		return this;
	}

	@Override
	public void loadState(final AwesomeConsoleConfig state) {
		XmlSerializerUtil.copyBean(state, this);
	}

	/**
	 * Helpers
	 */
	public static AwesomeConsoleConfig getInstance() {
		return ApplicationManager.getApplication().getComponent(AwesomeConsoleConfig.class);
	}

	private void initFromConfig() {
		form.limitLineMatchingByCheckBox.setSelected(LIMIT_LINE_LENGTH);

		form.matchLinesLongerThanCheckBox.setEnabled(LIMIT_LINE_LENGTH);
		form.matchLinesLongerThanCheckBox.setSelected(SPLIT_ON_LIMIT);

		form.searchForURLsFileCheckBox.setSelected(SEARCH_URLS);
		form.searchForFilesCheckBox.setSelected(SEARCH_FILES);

		form.maxLengthTextField.setText(String.valueOf(LINE_MAX_LENGTH));
		form.maxLengthTextField.setEnabled(LIMIT_LINE_LENGTH);
		form.maxLengthTextField.setEditable(LIMIT_LINE_LENGTH);
        form.loadData(this);
	}

	private void showErrorDialog() {
		JOptionPane.showMessageDialog(form.mainpanel, "Error: Please enter a positive number.", "Invalid value", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Configurable
	 */
	@Nls
	@Override
	public String getDisplayName() {
		return "Awesome Console";
	}

	@Nullable
	@Override
	public String getHelpTopic() {
		return "help topic";
	}

	@Nullable
	@Override
	public JComponent createComponent() {
		form = new AwesomeConsoleConfigForm();
		initFromConfig();
		return form.mainpanel;
	}

	@Override
	public boolean isModified() {
		final String text = form.maxLengthTextField.getText().trim();
		if (text.length() < 1) {
			return true;
		}
		int len;
		try {
			len = Integer.parseInt(text);
		} catch (NumberFormatException nfe) {
			return true;
		}
		return form.limitLineMatchingByCheckBox.isSelected() != LIMIT_LINE_LENGTH
				|| len != LINE_MAX_LENGTH
				|| form.matchLinesLongerThanCheckBox.isSelected() != SPLIT_ON_LIMIT
				|| form.searchForURLsFileCheckBox.isSelected() != SEARCH_URLS
				|| form.searchForFilesCheckBox.isSelected() != SEARCH_FILES
                || form.isModified(this);
	}

	@Override
	public void apply() throws ConfigurationException {
		final String text = form.maxLengthTextField.getText().trim();
		if (text.length() < 1) {
			showErrorDialog();
			return;
		}
		int i;
		try {
			i = Integer.parseInt(text);
		} catch (NumberFormatException nfe) {
			showErrorDialog();
			return;
		}
		if (i < 1) {
			showErrorDialog();
			return;
		}
		LIMIT_LINE_LENGTH = form.limitLineMatchingByCheckBox.isSelected();
		LINE_MAX_LENGTH = i;
		SPLIT_ON_LIMIT = form.matchLinesLongerThanCheckBox.isSelected();
		SEARCH_URLS = form.searchForURLsFileCheckBox.isSelected();
		SEARCH_FILES = form.searchForFilesCheckBox.isSelected();
        form.saveData(this);
		loadState(this);
	}

	@Override
	public void reset() {
		initFromConfig();
	}

	@Override
	public void disposeUIResources() {
		form = null;
	}

	/**
	 * ApplicationComponent
	 */
	@Override
	public void initComponent() {
	}

	@Override
	public void disposeComponent() {
	}

	@NotNull
	@Override
	public String getComponentName() {
		return "Awesome Console";
	}

    public String getExtensionsToAlwaysMatch() {
        return extensionsToAlwaysMatch;
    }

    public void setExtensionsToAlwaysMatch(final String extensionsToAlwaysMatch) {
        this.extensionsToAlwaysMatch = extensionsToAlwaysMatch;
    }

    public String getProjectSourcePaths() {
        return projectSourcePaths;
    }

    public void setProjectSourcePaths(final String projectSourcePaths) {
        this.projectSourcePaths = projectSourcePaths;
    }
}
