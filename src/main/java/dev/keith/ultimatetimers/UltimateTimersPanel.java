package dev.keith.ultimatetimers;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.image.BufferedImage;
import javax.swing.CellRendererPane;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.WindowConstants;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.Icon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.FontType;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;

/**
 * Side panel: one list per category (as many as the user adds), plus a permanent Unmapped list
 * at the bottom. Dragging a timer between lists picks which category's settings apply to it.
 * Timers in Unmapped (where a deleted category's timers go) are hidden. Each row also has its
 * own checkbox (click the left edge of the row) for whether that timer is shown at all.
 */
class UltimateTimersPanel extends PluginPanel
{
	private static final String CATEGORY_PROPERTY = "ultimateTimersCategory";
	/** Default Swing checkbox icons are ~16-18px wide; a little extra margin for an easy click. */
	private static final int CHECKBOX_HIT_WIDTH = 24;
	/** Each row's color swatch, on the right edge, and how much of the row's right side counts as clicking it. */
	private static final int SWATCH_WIDTH = 22;
	private static final int SWATCH_HEIGHT = 12;
	private static final int SWATCH_HIT_WIDTH = SWATCH_WIDTH + 12;

	private final UltimateTimersPlugin plugin;
	private final JTextField searchField = new JTextField();
	private final Map<TrackedBuff.TimerCategory, DefaultListModel<TrackedBuff>> models =
		new LinkedHashMap<>();

	private final Map<TrackedBuff.TimerCategory, JList<TrackedBuff>> lists =
		new LinkedHashMap<>();
	private final Map<TrackedBuff.TimerCategory, JButton> collapseButtons =
		new LinkedHashMap<>();
	private final Map<TrackedBuff.TimerCategory, JLabel> countLabels =
		new LinkedHashMap<>();
	private final Map<TrackedBuff.TimerCategory, JDialog> settingsDialogs =
		new LinkedHashMap<>();
	private final Map<TrackedBuff.TimerCategory, JPanel> sections =
		new LinkedHashMap<>();
	private final Map<TrackedBuff.TimerCategory, JTextField> nameFields =
		new LinkedHashMap<>();
	/** What collapsing hides: the Enable all/Disable all row (if any) plus the timer list. */
	private final Map<TrackedBuff.TimerCategory, JPanel> bodies =
		new LinkedHashMap<>();
	/** Holds one section per category (user categories in order, then Unmapped). Rebuilt on add/delete. */
	private final JPanel categoriesPanel = new JPanel();

	// Same total insets either way, so highlighting a drop target doesn't shift the layout.
	private static final Border SECTION_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createEmptyBorder(4, 0, 4, 0), BorderFactory.createEmptyBorder(2, 2, 2, 2));
	private static final Border SECTION_DROP_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createEmptyBorder(4, 0, 4, 0), BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 2));

	// Drag feedback state - only set while a timer is being dragged.
	private final CellRendererPane rendererPane = new CellRendererPane();
	private TrackedBuff draggingBuff;
	private TrackedBuff.TimerCategory dragSourceCategory;
	private TrackedBuff.TimerCategory highlightedCategory;
	private DragGhost ghost;
	private Point ghostOffset = new Point();
	private final DragSourceMotionListener dragMotionListener = e -> onDragMoved(e.getLocation());

	/** Closes any open category settings windows - called when the plugin shuts down. */
	void closeSettingsDialogs()
	{
		// Copy first: dispose() fires windowClosed, which removes from the map.
		for (JDialog dialog : new java.util.ArrayList<>(settingsDialogs.values()))
		{
			dialog.dispose();
		}
		settingsDialogs.clear();
	}

	/**
	 * Private MIME type (still carried as a plain String of the enum name) so text components -
	 * e.g. the category name field - don't treat a dragged timer as text and paste its name in.
	 * Only ever transferred within this JVM, so nothing is serialized.
	 */
	private static final DataFlavor TIMER_FLAVOR =
		new DataFlavor("application/x-ultimatetimers-timer; class=java.lang.String", "Ultimate Timers timer");

	/**
	 * Shared by every category's list, header and section, so a timer can be dropped onto a
	 * collapsed category's header as well as onto an expanded list. The target category is read
	 * from the drop component's CATEGORY_PROPERTY.
	 */
	private final TransferHandler transferHandler = new TransferHandler()
	{
		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		@SuppressWarnings("unchecked")
		protected Transferable createTransferable(JComponent c)
		{
			if (!(c instanceof JList))
			{
				return null;
			}
			JList<TrackedBuff> list = (JList<TrackedBuff>) c;
			TrackedBuff buff = list.getSelectedValue();
			if (buff == null)
			{
				return null;
			}
			startDragFeedback(this, list, list.getSelectedIndex(), buff);
			return new TimerTransferable(buff.name());
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action)
		{
			endDragFeedback();
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			if (support.isDrop())
			{
				// A drop lands in the whole category, not a particular row, so don't let the list
				// highlight an individual cell - the section outline shows where it's going.
				support.setShowDropLocation(false);
			}
			return support.isDataFlavorSupported(TIMER_FLAVOR)
				&& targetCategory(support.getComponent()) != null;
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support))
			{
				return false;
			}
			try
			{
				String buffName = (String) support.getTransferable().getTransferData(TIMER_FLAVOR);
				TrackedBuff buff = TrackedBuff.valueOf(buffName);
				plugin.setTimerCategory(buff, targetCategory(support.getComponent()));
				rebuild();
				return true;
			}
			catch (RuntimeException | java.io.IOException | java.awt.datatransfer.UnsupportedFlavorException e)
			{
				return false;
			}
		}
	};

	private static TrackedBuff.TimerCategory targetCategory(Component component)
	{
		return component instanceof JComponent
			? (TrackedBuff.TimerCategory) ((JComponent) component).getClientProperty(CATEGORY_PROPERTY)
			: null;
	}

	UltimateTimersPanel(UltimateTimersPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 8));

		JLabel title = new JLabel("Drag timers between categories to show/hide them and pick their "
			+ "flash/linger behavior");
		title.setForeground(Color.LIGHT_GRAY);

		searchField.setToolTipText("Filter by name");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});

		JButton expandAll = new JButton("Expand all");
		expandAll.setFocusPainted(false);
		expandAll.addActionListener(e -> setAllExpanded(true));
		JButton collapseAll = new JButton("Collapse all");
		collapseAll.setFocusPainted(false);
		collapseAll.addActionListener(e -> setAllExpanded(false));
		JPanel expandButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		expandButtons.add(expandAll);
		expandButtons.add(collapseAll);

		JButton addCategory = new JButton("Add category");
		addCategory.setFocusPainted(false);
		addCategory.addActionListener(e -> addCategory());
		JPanel buttons = new JPanel(new BorderLayout(0, 4));
		buttons.add(expandButtons, BorderLayout.NORTH);
		buttons.add(addCategory, BorderLayout.SOUTH);

		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.add(title, BorderLayout.NORTH);
		header.add(searchField, BorderLayout.CENTER);
		header.add(buttons, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		categoriesPanel.setLayout(new BoxLayout(categoriesPanel, BoxLayout.Y_AXIS));
		add(categoriesPanel, BorderLayout.CENTER);

		JButton resetDefaults = new JButton("Reset Default Categories");
		resetDefaults.setFocusPainted(false);
		resetDefaults.setToolTipText("Bring back the original 5 categories with default names and settings, "
			+ "and move every timer back to its default category");
		resetDefaults.addActionListener(e -> resetDefaultCategories());
		JButton resetColors = new JButton("Reset Default Colors");
		resetColors.setFocusPainted(false);
		resetColors.setToolTipText("Put every timer's own color (the squares on the right) back to its default");
		resetColors.addActionListener(e -> resetDefaultColors());
		JButton exportSettings = new JButton("Export Settings...");
		exportSettings.setFocusPainted(false);
		exportSettings.setToolTipText("Save all of this plugin's settings to a file, to back them up or share them");
		exportSettings.addActionListener(e -> exportSettings());
		JButton importSettings = new JButton("Import Settings...");
		importSettings.setFocusPainted(false);
		importSettings.setToolTipText("Replace all of this plugin's settings with ones from an exported file");
		importSettings.addActionListener(e -> importSettings());
		JPanel transferButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		transferButtons.add(exportSettings);
		transferButtons.add(importSettings);

		JPanel resetButtons = new JPanel(new GridLayout(0, 1, 0, 4));
		resetButtons.add(resetDefaults);
		resetButtons.add(resetColors);
		resetButtons.add(transferButtons);
		add(resetButtons, BorderLayout.SOUTH);

		buildSections();
	}

	/** (Re)creates every category section - on startup and whenever a category is added or deleted. */
	private void buildSections()
	{
		endDragFeedback();
		categoriesPanel.removeAll();
		models.clear();
		lists.clear();
		bodies.clear();
		collapseButtons.clear();
		countLabels.clear();
		sections.clear();
		nameFields.clear();
		for (TrackedBuff.TimerCategory category : plugin.getCategories())
		{
			categoriesPanel.add(buildCategorySection(category));
		}
		categoriesPanel.add(buildCategorySection(TrackedBuff.TimerCategory.UNMAPPED));
		rebuild();
	}

	private void addCategory()
	{
		TrackedBuff.TimerCategory category = plugin.addCategory();
		// Open, so it's obvious where it went and ready to drag timers into.
		plugin.setCategoryExpanded(category, true);
		buildSections();
		JTextField nameField = nameFields.get(category);
		if (nameField != null)
		{
			// Ready to type a name straight away.
			nameField.requestFocusInWindow();
			nameField.selectAll();
			nameField.scrollRectToVisible(nameField.getBounds());
		}
	}

	private static final String SETTINGS_FILE_DESCRIPTION = "Ultimate Timers settings (*.json)";
	/** Way bigger than any real settings file - just guards against picking something huge by mistake. */
	private static final long MAX_SETTINGS_FILE_BYTES = 2L * 1024 * 1024;

	private void exportSettings()
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Export Ultimate Timers Settings")
			.addExtensionFilter(SETTINGS_FILE_DESCRIPTION, "json")
			.setDefaultExtension("json")
			.setFileName("ultimate-timers-settings.json")
			.showDialog(this);
		if (chosen.isEmpty())
		{
			return;
		}
		Filepath file = chosen.get(0);
		try
		{
			file.write(plugin.exportSettings());
			JOptionPane.showMessageDialog(this, "Settings exported to:\n" + file, "Export Settings",
				JOptionPane.INFORMATION_MESSAGE);
		}
		catch (IOException | RuntimeException e)
		{
			JOptionPane.showMessageDialog(this, "Couldn't save the settings file:\n" + e.getMessage(),
				"Export Settings", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importSettings()
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Import Ultimate Timers Settings")
			.addExtensionFilter(SETTINGS_FILE_DESCRIPTION, "json")
			.showDialog(this);
		if (chosen.isEmpty())
		{
			return;
		}
		Filepath file = chosen.get(0);
		String json;
		int count;
		try
		{
			if (file.size() > MAX_SETTINGS_FILE_BYTES)
			{
				throw new IllegalArgumentException("That file is too big to be a settings file.");
			}
			try (java.io.Reader reader = file.openReader())
			{
				StringBuilder text = new StringBuilder();
				char[] buffer = new char[8192];
				int read;
				while ((read = reader.read(buffer)) != -1)
				{
					text.append(buffer, 0, read);
				}
				json = text.toString();
			}
			count = plugin.validateSettings(json);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Couldn't read that file:\n" + e.getMessage(), "Import Settings",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		catch (IllegalArgumentException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Import Settings", JOptionPane.ERROR_MESSAGE);
			return;
		}

		int choice = JOptionPane.showConfirmDialog(this, "Import " + count + " settings from " + file.getFileName()
			+ "?\n\nThis replaces ALL of your current Ultimate Timers settings: categories, timer colors, "
			+ "on/off choices and timer positions.\nTip: export your current settings first if you might "
			+ "want them back.", "Import Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		closeSettingsDialogs();
		plugin.importSettings(json, () ->
		{
			buildSections();
			JOptionPane.showMessageDialog(this, "Settings imported.", "Import Settings",
				JOptionPane.INFORMATION_MESSAGE);
		});
	}

	private void resetDefaultColors()
	{
		int choice = JOptionPane.showConfirmDialog(this, "Reset every timer's color to its default?\n\n"
			+ "This is the color square on each timer's row. Category colors (border, background, "
			+ "override and percentage colors) aren't changed.",
			"Reset Default Colors", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		plugin.resetTimerColors();
		getWrappedPanel().repaint();
	}

	private void resetDefaultCategories()
	{
		int choice = JOptionPane.showConfirmDialog(this, "Reset the 5 default categories?\n\n"
			+ "- Any of them you deleted come back.\n"
			+ "- Their names and settings go back to default.\n"
			+ "- Every timer moves back to its default category.\n\n"
			+ "Categories you added keep their settings but will be empty.\n"
			+ "Timer on/off checkboxes and timer colors aren't changed.",
			"Reset Default Categories", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		// Their settings windows would show the old values.
		for (TrackedBuff.TimerCategory category : TrackedBuff.TimerCategory.DEFAULTS)
		{
			JDialog dialog = settingsDialogs.remove(category);
			if (dialog != null)
			{
				dialog.dispose();
			}
		}
		plugin.resetDefaultCategories();
		buildSections();
	}

	private void deleteCategory(TrackedBuff.TimerCategory category)
	{
		int timerCount = 0;
		for (TrackedBuff buff : TrackedBuff.values())
		{
			if (plugin.getTimerCategory(buff) == category)
			{
				timerCount++;
			}
		}
		String message = "Delete \"" + plugin.getCategoryName(category) + "\" and its settings?"
			+ (timerCount == 0 ? "" : "\n\nIts " + timerCount + (timerCount == 1 ? " timer" : " timers")
			+ " will move to Unmapped and won't be shown until you move them to another category.");
		int choice = JOptionPane.showConfirmDialog(this, message, "Delete category", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		JDialog dialog = settingsDialogs.remove(category);
		if (dialog != null)
		{
			dialog.dispose();
		}
		plugin.deleteCategory(category);
		buildSections();
	}

	@Override
	public void onActivate()
	{
		// Refreshes the lists each time the panel is opened.
		rebuild();
	}

	private JPanel buildCategorySection(TrackedBuff.TimerCategory category)
	{
		DefaultListModel<TrackedBuff> model = new DefaultListModel<>();
		models.put(category, model);

		JList<TrackedBuff> list = new JList<>(model);
		lists.put(category, list);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setDragEnabled(true);
		list.setDropMode(DropMode.ON);
		list.setTransferHandler(transferHandler);
		list.putClientProperty(CATEGORY_PROPERTY, category);
		list.setCellRenderer(new TimerCheckBoxRenderer());
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = list.locationToIndex(e.getPoint());
				if (index < 0)
				{
					return;
				}
				Rectangle bounds = list.getCellBounds(index, index);
				if (bounds == null || !bounds.contains(e.getPoint()) || category.isUnmapped())
				{
					// Unmapped timers are never shown, so their checkbox and color can't be changed here.
					return;
				}
				TrackedBuff buff = list.getModel().getElementAt(index);
				if (e.getX() - bounds.x <= CHECKBOX_HIT_WIDTH)
				{
					plugin.setTimerEnabled(buff, !plugin.isTimerEnabled(buff));
					list.repaint(bounds);
				}
				else if (bounds.x + bounds.width - e.getX() <= SWATCH_HIT_WIDTH)
				{
					Color chosen = JColorChooser.showDialog(list, buff.getLabel() + " color", plugin.getTimerColor(buff));
					if (chosen != null)
					{
						plugin.setTimerColor(buff, chosen);
						list.repaint(bounds);
					}
				}
			}
		});

		// Editable in place - click in, type a name, Enter or click away to save.
		JTextField nameField = new JTextField(plugin.getCategoryName(category));
		nameField.setFont(nameField.getFont().deriveFont(Font.BOLD));
		nameField.setHorizontalAlignment(JTextField.CENTER);
		nameFields.put(category, nameField);
		if (category.isUnmapped())
		{
			nameField.setEditable(false);
			nameField.setToolTipText("Timers here are hidden. Drag one into a category to show it again.");
		}
		Runnable commit = () ->
		{
			plugin.setCategoryName(category, nameField.getText());
			nameField.setText(plugin.getCategoryName(category));
		};
		nameField.addActionListener(e -> commit.run());
		nameField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
		// A text field has its own drop handling, which would swallow a timer dropped on the
		// header's name. Route timer drops to the shared handler, everything else (typing, copy,
		// paste, text drops) to the field's original handler.
		nameField.putClientProperty(CATEGORY_PROPERTY, category);
		nameField.setTransferHandler(new TimerDropDelegatingHandler(nameField.getTransferHandler()));

		// Expanded/collapsed state is saved per category (collapsed by default) so the headers
		// fit on screen. Icons are drawn rather than ▶/▼/⚙ text, since RuneLite's fonts don't
		// necessarily have those glyphs.
		JButton collapseButton = new JButton(new ArrowIcon(false));
		collapseButton.setMargin(new Insets(2, 4, 2, 4));
		collapseButton.setFocusPainted(false);
		collapseButton.setToolTipText("Expand/collapse");
		collapseButton.addActionListener(e -> toggleExpanded(category));
		collapseButtons.put(category, collapseButton);

		JLabel countLabel = new JLabel();
		countLabel.setForeground(Color.GRAY);
		countLabel.setToolTipText("Timers in this category");
		countLabels.put(category, countLabel);

		JButton settingsButton = new JButton(new GearIcon());
		settingsButton.setToolTipText("Settings for " + plugin.getCategoryName(category));
		settingsButton.setMargin(new Insets(2, 4, 2, 4));
		settingsButton.setFocusPainted(false);
		settingsButton.addActionListener(e -> openSettingsDialog(category));

		JPanel east = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
		east.add(countLabel);
		// Unmapped has no settings (its timers are hidden) and can't be deleted.
		if (!category.isUnmapped())
		{
			east.add(settingsButton);
			JButton deleteButton = new JButton(new DeleteIcon());
			deleteButton.setToolTipText("Delete " + plugin.getCategoryName(category));
			deleteButton.setMargin(new Insets(2, 4, 2, 4));
			deleteButton.setFocusPainted(false);
			deleteButton.addActionListener(e -> deleteCategory(category));
			east.add(deleteButton);
		}

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.add(collapseButton, BorderLayout.WEST);
		header.add(nameField, BorderLayout.CENTER);
		header.add(east, BorderLayout.EAST);
		// Drops anywhere on the header (buttons and count label included - they have no drop
		// target of their own, so AWT hands the drop to the nearest ancestor that does) land in
		// this category even while it's collapsed.
		header.putClientProperty(CATEGORY_PROPERTY, category);
		header.setTransferHandler(transferHandler);

		JPanel section = new JPanel(new BorderLayout(0, 4));
		section.setBorder(SECTION_BORDER);
		sections.put(category, section);
		section.putClientProperty(CATEGORY_PROPERTY, category);
		section.setTransferHandler(transferHandler);
		section.add(header, BorderLayout.NORTH);
		// No JScrollPane here deliberately - every item in every category should always be
		// visible without an extra scroll step once expanded; the panel's own outer scroll (from
		// PluginPanel) handles scrolling the whole thing instead.
		JPanel body = new JPanel(new BorderLayout(0, 4));
		body.setOpaque(false);
		body.putClientProperty(CATEGORY_PROPERTY, category);
		body.setTransferHandler(transferHandler);
		if (!category.isUnmapped())
		{
			JButton enableAll = new JButton("Enable all");
			enableAll.setFocusPainted(false);
			enableAll.setToolTipText("Turn on every timer listed in this category (only the search matches while searching)");
			enableAll.addActionListener(e -> setListedTimersEnabled(category, true));
			JButton disableAll = new JButton("Disable all");
			disableAll.setFocusPainted(false);
			disableAll.setToolTipText("Turn off every timer listed in this category (only the search matches while searching)");
			disableAll.addActionListener(e -> setListedTimersEnabled(category, false));
			JPanel enableButtons = new JPanel(new GridLayout(1, 2, 4, 0));
			enableButtons.setOpaque(false);
			enableButtons.add(enableAll);
			enableButtons.add(disableAll);
			body.add(enableButtons, BorderLayout.NORTH);
		}
		body.add(list, BorderLayout.CENTER);
		bodies.put(category, body);
		section.add(body, BorderLayout.CENTER);

		return section;
	}

	/**
	 * Everything that used to be a static, global config section (Size & Shape, Text & Icon
	 * Layout, Advanced Behaviors (Category N), and the two main Debug toggles) is per-category
	 * now and lives only here - one dialog per category, tabbed since that's ~30 settings total.
	 * Applies changes immediately, same as RuneLite's own config panel.
	 */
	private void openSettingsDialog(TrackedBuff.TimerCategory category)
	{
		// One window per category: clicking the gear again just brings the open one forward.
		JDialog existing = settingsDialogs.get(category);
		if (existing != null && existing.isDisplayable())
		{
			existing.setVisible(true);
			existing.toFront();
			existing.requestFocus();
			return;
		}

		// Owned by the RuneLite window, so it always stays in front of the game client instead of
		// getting lost behind it.
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
			plugin.getCategoryName(category) + " Settings");
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				settingsDialogs.remove(category, dialog);
			}
		});
		settingsDialogs.put(category, dialog);
		JTabbedPane tabs = new JTabbedPane();
		// Pinned to the top of each tab so a shorter tab doesn't spread its rows out to match the
		// tallest one.
		tabs.addTab("Appearance", pinnedToTop(appearanceTab(category)));
		tabs.addTab("Text & Icon", pinnedToTop(textIconTab(category)));
		tabs.addTab("Advanced Behaviors", pinnedToTop(advancedBehaviorsTab(category)));
		dialog.add(tabs, BorderLayout.CENTER);
		dialog.add(debugFooter(category), BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	/**
	 * Debug toggles sit below the tabs so they're visible whichever tab is open. Handy for
	 * previewing changes on any tab without hunting for the switch.
	 */
	private JPanel debugFooter(TrackedBuff.TimerCategory category)
	{
		JCheckBox forceShow = new JCheckBox("Force show", plugin.isForceShowEnabled(category));
		forceShow.setToolTipText("Show every enabled timer in this category at once, using the global preview "
			+ "time/remaining settings");
		forceShow.addActionListener(e -> plugin.setForceShowEnabled(category, forceShow.isSelected()));

		JCheckBox forceFlash = new JCheckBox("Force flashing", plugin.isForceFlashEnabled(category));
		forceFlash.setToolTipText("Make every timer in this category flash regardless of its remaining time "
			+ "(still requires \"Flash when low\" to be on)");
		forceFlash.addActionListener(e -> plugin.setForceFlashEnabled(category, forceFlash.isSelected()));

		JLabel title = new JLabel("Debug");
		title.setFont(title.getFont().deriveFont(Font.BOLD));

		JPanel toggles = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		toggles.add(title);
		toggles.add(forceShow);
		toggles.add(forceFlash);

		JButton applyToOthers = new JButton("Apply to Other Categories...");
		applyToOthers.setFocusPainted(false);
		applyToOthers.setToolTipText("Copy all of this category's settings onto other categories");
		applyToOthers.addActionListener(e -> openApplyToOthersDialog(category, applyToOthers));

		JPanel footer = new JPanel(new BorderLayout(8, 4));
		footer.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 8));
		footer.add(new JSeparator(), BorderLayout.NORTH);
		footer.add(toggles, BorderLayout.CENTER);
		JButton resetToDefault = new JButton("Reset to Default");
		resetToDefault.setFocusPainted(false);
		resetToDefault.setToolTipText("Put every setting in this window back to its default. The name and "
			+ "timers stay as they are.");
		resetToDefault.addActionListener(e -> resetCategoryToDefault(category, resetToDefault));

		// On their own row under the Debug toggles.
		// Left-aligned with the Debug row above (whose FlowLayout starts 8px in).
		JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
		actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		actions.add(resetToDefault);
		actions.add(applyToOthers);

		footer.add(actions, BorderLayout.SOUTH);
		return footer;
	}

	private void resetCategoryToDefault(TrackedBuff.TimerCategory category, Component invoker)
	{
		java.awt.Window window = SwingUtilities.getWindowAncestor(invoker);
		int choice = JOptionPane.showConfirmDialog(window, "Reset every setting of \"" + plugin.getCategoryName(category)
			+ "\" to its default?\n\nIts name and timers stay as they are.", "Reset to Default",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return;
		}
		plugin.resetCategorySettings(category);
		getWrappedPanel().repaint();
		reopenSettingsDialog(category, window);
	}

	/** Rebuilds a category's settings window in the same spot, so its controls show the current values. */
	private void reopenSettingsDialog(TrackedBuff.TimerCategory category, java.awt.Window old)
	{
		java.awt.Point location = old != null ? old.getLocation() : null;
		JDialog existing = settingsDialogs.remove(category);
		if (existing != null)
		{
			existing.dispose();
		}
		openSettingsDialog(category);
		JDialog reopened = settingsDialogs.get(category);
		if (reopened != null && location != null)
		{
			reopened.setLocation(location);
		}
	}

	/**
	 * Lets the user tick which other categories should get a copy of this one's settings.
	 * Modal and owned by the settings window, so it stays on top of it.
	 */
	private void openApplyToOthersDialog(TrackedBuff.TimerCategory source, Component invoker)
	{
		java.util.List<TrackedBuff.TimerCategory> others = new java.util.ArrayList<>(plugin.getCategories());
		others.remove(source);
		java.awt.Window owner = SwingUtilities.getWindowAncestor(invoker);
		if (others.isEmpty())
		{
			JOptionPane.showMessageDialog(owner, "There are no other categories to copy to.",
				"Apply to Other Categories", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JDialog picker = new JDialog(owner, "Apply to Other Categories", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		picker.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		FormPanel form = new FormPanel();
		JLabel intro = new JLabel("<html>Copy every setting of <b>" + escapeHtml(plugin.getCategoryName(source))
			+ "</b> onto:</html>");
		form.add(intro);

		Map<TrackedBuff.TimerCategory, JCheckBox> boxes = new LinkedHashMap<>();
		JCheckBox selectAll = new JCheckBox("Select all");
		form.add(selectAll);
		form.add(new JSeparator());
		for (TrackedBuff.TimerCategory category : others)
		{
			JCheckBox box = new JCheckBox(plugin.getCategoryName(category));
			boxes.put(category, box);
			form.add(box);
		}

		JButton apply = new JButton("Apply");
		apply.setEnabled(false);
		Runnable updateState = () ->
		{
			long ticked = boxes.values().stream().filter(JCheckBox::isSelected).count();
			apply.setEnabled(ticked > 0);
			selectAll.setSelected(ticked == boxes.size());
		};
		for (JCheckBox box : boxes.values())
		{
			box.addActionListener(e -> updateState.run());
		}
		selectAll.addActionListener(e ->
		{
			boolean select = selectAll.isSelected();
			boxes.values().forEach(b -> b.setSelected(select));
			updateState.run();
		});

		JLabel note = new JLabel("<html>Names, timers and the Debug toggles aren't copied.</html>");
		note.setForeground(Color.GRAY);
		form.add(note);

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> picker.dispose());
		apply.addActionListener(e ->
		{
			for (Map.Entry<TrackedBuff.TimerCategory, JCheckBox> entry : boxes.entrySet())
			{
				if (entry.getValue().isSelected())
				{
					plugin.copyCategorySettings(source, entry.getKey());
					// An open settings window for a target would show its old values - close it so
					// it reopens with the copied ones.
					JDialog stale = settingsDialogs.remove(entry.getKey());
					if (stale != null)
					{
						stale.dispose();
					}
				}
			}
			picker.dispose();
			getWrappedPanel().repaint();
		});

		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
		buttons.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		buttons.add(cancel);
		buttons.add(apply);

		picker.add(pinnedToTop(form), BorderLayout.CENTER);
		picker.add(buttons, BorderLayout.SOUTH);
		picker.getRootPane().setDefaultButton(apply);
		picker.pack();
		picker.setLocationRelativeTo(owner);
		picker.setVisible(true);
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private JPanel appearanceTab(TrackedBuff.TimerCategory category)
	{
		FormPanel panel = new FormPanel();

		panel.addHeader("Size & Shape");

		JSpinner width = new JSpinner(new SpinnerNumberModel(plugin.getRingWidth(category), 16, 200, 1));
		width.addChangeListener(e -> plugin.setRingWidth(category, (int) width.getValue()));
		panel.add(labeled("Width", width));

		JSpinner height = new JSpinner(new SpinnerNumberModel(plugin.getRingHeight(category), 16, 200, 1));
		height.addChangeListener(e -> plugin.setRingHeight(category, (int) height.getValue()));
		panel.add(labeled("Height", height));

		JSpinner cornerRadius = new JSpinner(new SpinnerNumberModel(plugin.getCornerRadius(category), 0, 100, 1));
		cornerRadius.addChangeListener(e -> plugin.setCornerRadius(category, (int) cornerRadius.getValue()));
		panel.add(labeled("Corner radius", cornerRadius));

		JSpinner padding = new JSpinner(new SpinnerNumberModel(plugin.getGroupPadding(category), 0, 40, 1));
		padding.addChangeListener(e -> plugin.setGroupPadding(category, (int) padding.getValue()));
		panel.add(labeled("Padding", padding));

		panel.addHeader("Border");

		JSpinner borderThickness =
			new JSpinner(new SpinnerNumberModel(plugin.getRingBorderThickness(category), 0, 10, 1));
		borderThickness.addChangeListener(e -> plugin.setRingBorderThickness(category, (int) borderThickness.getValue()));
		panel.add(labeled("Border thickness", borderThickness));

		panel.add(labeled("Border color", colorButton(plugin.getBorderColor(category),
			c -> plugin.setBorderColor(category, c))));

		panel.addHeader("Fill");

		panel.add(labeled("Background color", colorButton(plugin.getBackgroundColor(category),
			c -> plugin.setBackgroundColor(category, c))));

		// Shown as "Fill style"; still the fillDirection setting underneath.
		JComboBox<FillDirection> fillStyle = new JComboBox<>(FillDirection.values());
		fillStyle.setSelectedItem(plugin.getFillDirection(category));
		fillStyle.addActionListener(
			e -> plugin.setFillDirection(category, (FillDirection) fillStyle.getSelectedItem()));
		panel.add(labeled("Fill style", fillStyle));

		addColorStyleRows(panel, category);

		return panel;
	}

	private void addColorStyleRows(FormPanel panel, TrackedBuff.TimerCategory category)
	{
		JComboBox<ColorStyle> colorStyle = new JComboBox<>(ColorStyle.values());
		colorStyle.setSelectedItem(plugin.getColorStyle(category));
		colorStyle.setToolTipText("How the foreground (fill) color is chosen for timers in this category. "
			+ "Border, background and text colors aren't affected.");
		panel.add(labeled("Color style", colorStyle));

		// Only the rows that matter for the selected style are shown.
		JLabel individualHint = new JLabel("<html>Set each timer's color with the color square on its row "
			+ "in the side panel.</html>");
		individualHint.setForeground(Color.GRAY);
		panel.add(individualHint);

		JPanel overrideRow = labeled("Override color", colorButton(plugin.getOverrideColor(category),
			c -> plugin.setOverrideColor(category, c)));
		panel.add(overrideRow);

		JPanel fullRow = labeled("Color at 100%", colorButton(plugin.getPercentColorFull(category),
			c -> plugin.setPercentColorFull(category, c)));
		JPanel halfRow = labeled("Color at 50%", colorButton(plugin.getPercentColorHalf(category),
			c -> plugin.setPercentColorHalf(category, c)));
		JPanel emptyRow = labeled("Color at 25% and below", colorButton(plugin.getPercentColorEmpty(category),
			c -> plugin.setPercentColorEmpty(category, c)));
		panel.add(fullRow);
		panel.add(halfRow);
		panel.add(emptyRow);

		Runnable showRowsForStyle = () ->
		{
			ColorStyle style = (ColorStyle) colorStyle.getSelectedItem();
			individualHint.setVisible(style == ColorStyle.INDIVIDUAL);
			overrideRow.setVisible(style == ColorStyle.OVERRIDE);
			fullRow.setVisible(style == ColorStyle.PERCENTAGE);
			halfRow.setVisible(style == ColorStyle.PERCENTAGE);
			emptyRow.setVisible(style == ColorStyle.PERCENTAGE);
			panel.revalidate();
			java.awt.Window window = SwingUtilities.getWindowAncestor(panel);
			if (window != null)
			{
				window.pack();
			}
		};
		showRowsForStyle.run();
		colorStyle.addActionListener(e ->
		{
			plugin.setColorStyle(category, (ColorStyle) colorStyle.getSelectedItem());
			showRowsForStyle.run();
			// The rows' color swatches fade when individual colors aren't in use.
			getWrappedPanel().repaint();
		});
	}

	private JPanel textIconTab(TrackedBuff.TimerCategory category)
	{
		FormPanel panel = new FormPanel();

		panel.addHeader("Time");

		JCheckBox showTime = new JCheckBox("Show time", plugin.isShowTimeText(category));
		showTime.addActionListener(e -> plugin.setShowTimeText(category, showTime.isSelected()));
		panel.add(showTime);

		JComboBox<ElementPosition> textPosition = new JComboBox<>(ElementPosition.values());
		textPosition.setSelectedItem(plugin.getTextPosition(category));
		textPosition.addActionListener(
			e -> plugin.setTextPosition(category, (ElementPosition) textPosition.getSelectedItem()));
		panel.add(labeled("Time position", textPosition));

		JSpinner textOffset = new JSpinner(new SpinnerNumberModel(plugin.getTextOffset(category), -50, 50, 1));
		textOffset.setToolTipText(OFFSET_TOOLTIP);
		textOffset.addChangeListener(e -> plugin.setTextOffset(category, (int) textOffset.getValue()));
		panel.add(labeled("Time offset", textOffset));

		panel.addHeader("Description");

		JCheckBox showDescription = new JCheckBox("Show description", plugin.isShowDescription(category));
		showDescription.addActionListener(e -> plugin.setShowDescription(category, showDescription.isSelected()));
		panel.add(showDescription);

		JComboBox<ElementPosition> descriptionPosition = new JComboBox<>(ElementPosition.values());
		descriptionPosition.setSelectedItem(plugin.getDescriptionPosition(category));
		descriptionPosition.addActionListener(
			e -> plugin.setDescriptionPosition(category, (ElementPosition) descriptionPosition.getSelectedItem()));
		panel.add(labeled("Description position", descriptionPosition));

		JSpinner descriptionOffset =
			new JSpinner(new SpinnerNumberModel(plugin.getDescriptionOffset(category), -50, 50, 1));
		descriptionOffset.setToolTipText(OFFSET_TOOLTIP);
		descriptionOffset.addChangeListener(
			e -> plugin.setDescriptionOffset(category, (int) descriptionOffset.getValue()));
		panel.add(labeled("Description offset", descriptionOffset));

		panel.addHeader("Icon");

		JCheckBox showIcon = new JCheckBox("Show icon", plugin.isShowIcon(category));
		showIcon.addActionListener(e -> plugin.setShowIcon(category, showIcon.isSelected()));
		panel.add(showIcon);

		JComboBox<ElementPosition> iconPosition = new JComboBox<>(ElementPosition.values());
		iconPosition.setSelectedItem(plugin.getIconPosition(category));
		iconPosition.addActionListener(
			e -> plugin.setIconPosition(category, (ElementPosition) iconPosition.getSelectedItem()));
		panel.add(labeled("Icon position", iconPosition));

		JSpinner iconOffset = new JSpinner(new SpinnerNumberModel(plugin.getIconOffset(category), -50, 50, 1));
		iconOffset.setToolTipText(OFFSET_TOOLTIP);
		iconOffset.addChangeListener(e -> plugin.setIconOffset(category, (int) iconOffset.getValue()));
		panel.add(labeled("Icon offset", iconOffset));

		JSpinner iconSize = new JSpinner(new SpinnerNumberModel(plugin.getIconSize(category), 8, 64, 1));
		iconSize.addChangeListener(e -> plugin.setIconSize(category, (int) iconSize.getValue()));
		panel.add(labeled("Icon size", iconSize));

		// Font/color/border/shadow are shared by the time and description text.
		panel.addHeader("Text Style");

		// One saved font setting (family, size, bold, italic), edited through three controls.
		// Each change re-reads the current font so the other two parts are kept.
		FontType currentFont = plugin.getTextFont(category);

		JComboBox<String> fontFamily = new JComboBox<>(fontFamilies(currentFont.getFamily()));
		fontFamily.setSelectedItem(currentFont.getFamily());
		fontFamily.addActionListener(e ->
		{
			FontType f = plugin.getTextFont(category);
			plugin.setTextFont(category,
				newFont((String) fontFamily.getSelectedItem(), f.getSize(), f.isBold(), f.isItalic()));
		});
		panel.add(labeled("Text font", fontFamily));

		JSpinner fontSize = new JSpinner(new SpinnerNumberModel(
			Math.max(6, Math.min(48, currentFont.getSize())), 6, 48, 1));
		fontSize.addChangeListener(e ->
		{
			FontType f = plugin.getTextFont(category);
			plugin.setTextFont(category, newFont(f.getFamily(), (int) fontSize.getValue(), f.isBold(), f.isItalic()));
		});
		panel.add(labeled("Font size", fontSize));

		JComboBox<FontStyle> fontStyle = new JComboBox<>(FontStyle.values());
		fontStyle.setSelectedItem(FontStyle.of(currentFont.isBold(), currentFont.isItalic()));
		fontStyle.addActionListener(e ->
		{
			FontStyle style = (FontStyle) fontStyle.getSelectedItem();
			FontType f = plugin.getTextFont(category);
			plugin.setTextFont(category, newFont(f.getFamily(), f.getSize(), style.bold, style.italic));
		});
		panel.add(labeled("Font style", fontStyle));

		panel.add(labeled("Text color", colorButton(plugin.getTextColor(category),
			c -> plugin.setTextColor(category, c))));

		JCheckBox textBorder = new JCheckBox("Text border", plugin.isTextBorderEnabled(category));
		textBorder.addActionListener(e -> plugin.setTextBorderEnabled(category, textBorder.isSelected()));
		panel.add(textBorder);

		panel.add(labeled("Text border color", colorButton(plugin.getTextBorderColor(category),
			c -> plugin.setTextBorderColor(category, c))));

		JCheckBox textShadow = new JCheckBox("Text shadow", plugin.isTextShadowEnabled(category));
		textShadow.addActionListener(e -> plugin.setTextShadowEnabled(category, textShadow.isSelected()));
		panel.add(textShadow);

		return panel;
	}

	private JPanel advancedBehaviorsTab(TrackedBuff.TimerCategory category)
	{
		FormPanel panel = new FormPanel();

		panel.addHeader("Flash");

		JCheckBox enableFlash = new JCheckBox("Flash when low", plugin.isFlashEnabled(category));
		enableFlash.addActionListener(e -> plugin.setFlashEnabled(category, enableFlash.isSelected()));
		panel.add(enableFlash);

		// No upper limit (null maximum), for long timers you want flagged well before they run out.
		JSpinner threshold = new JSpinner(new SpinnerNumberModel(
			Integer.valueOf(plugin.getFlashThresholdSeconds(category)), Integer.valueOf(1), null, Integer.valueOf(1)));
		((JSpinner.DefaultEditor) threshold.getEditor()).getTextField().setColumns(5);
		threshold.addChangeListener(e -> plugin.setFlashThresholdSeconds(category, (int) threshold.getValue()));
		panel.add(labeled("Flash threshold (seconds)", threshold));

		panel.add(labeled("Flash color", colorButton(plugin.getFlashColor(category),
			c -> plugin.setFlashColor(category, c))));

		JCheckBox flashForeground = new JCheckBox("Flash foreground", plugin.isFlashForeground(category));
		flashForeground.addActionListener(e -> plugin.setFlashForeground(category, flashForeground.isSelected()));
		panel.add(flashForeground);

		JCheckBox flashBackground = new JCheckBox("Flash background", plugin.isFlashBackground(category));
		flashBackground.addActionListener(e -> plugin.setFlashBackground(category, flashBackground.isSelected()));
		panel.add(flashBackground);

		JCheckBox flashBorder = new JCheckBox("Flash border", plugin.isFlashBorder(category));
		flashBorder.addActionListener(e -> plugin.setFlashBorder(category, flashBorder.isSelected()));
		panel.add(flashBorder);

		JCheckBox flashText = new JCheckBox("Flash text", plugin.isFlashText(category));
		flashText.addActionListener(e -> plugin.setFlashText(category, flashText.isSelected()));
		panel.add(flashText);

		panel.addHeader("Linger");

		JCheckBox enableLinger = new JCheckBox("Linger after finishing", plugin.isLingerEnabled(category));
		enableLinger.addActionListener(e -> plugin.setLingerEnabled(category, enableLinger.isSelected()));
		panel.add(enableLinger);

		JSpinner lingerSeconds = new JSpinner(
			new SpinnerNumberModel(plugin.getLingerSecondsSetting(category), 1, 120, 1));
		lingerSeconds.addChangeListener(e -> plugin.setLingerSeconds(category, (int) lingerSeconds.getValue()));
		panel.add(labeled("Linger duration (seconds)", lingerSeconds));

		panel.addHeader("Visual");

		JCheckBox fixedMaximum = new JCheckBox("Use a fixed maximum", plugin.isFixedMaximumEnabled(category));
		fixedMaximum.setToolTipText("Measure every timer's fill against the same length instead of its own "
			+ "starting duration. Timers longer than this show as full until they drop below it.");
		fixedMaximum.addActionListener(e -> plugin.setFixedMaximumEnabled(category, fixedMaximum.isSelected()));
		panel.add(fixedMaximum);

		JSpinner fixedMaximumSeconds = new JSpinner(new SpinnerNumberModel(
			Integer.valueOf(plugin.getFixedMaximumSeconds(category)), Integer.valueOf(1), null, Integer.valueOf(1)));
		((JSpinner.DefaultEditor) fixedMaximumSeconds.getEditor()).getTextField().setColumns(5);
		fixedMaximumSeconds.addChangeListener(
			e -> plugin.setFixedMaximumSeconds(category, (int) fixedMaximumSeconds.getValue()));
		panel.add(labeled("Fixed maximum (seconds)", fixedMaximumSeconds));

		panel.addHeader("Tooltip");

		JCheckBox showTooltips = new JCheckBox("Show tooltips", plugin.isTooltipsEnabled(category));
		showTooltips.addActionListener(e -> plugin.setTooltipsEnabled(category, showTooltips.isSelected()));
		panel.add(showTooltips);

		return panel;
	}

	private static final String OFFSET_TOOLTIP = "Moves it toward its position's side: up/down for "
		+ "top/bottom/above/below, left/right for the sides, diagonally for corners. Negative moves it back "
		+ "toward the center. Does nothing at Center.";

	/**
	 * Built with the constructor rather than FontType's with...() methods: those copy the
	 * already-built (cached) Font across, so a new size or style wouldn't take effect.
	 */
	private static FontType newFont(String family, int size, boolean bold, boolean italic)
	{
		return new FontType(family, size, bold, italic, null);
	}

	/** RuneLite's built-in fonts first, then any custom and system fonts, plus the current one if it's missing. */
	private static String[] fontFamilies(String current)
	{
		java.util.LinkedHashSet<String> families = new java.util.LinkedHashSet<>();
		families.add(FontManager.getRunescapeFont().getFamily());
		families.add(FontManager.getRunescapeBoldFont().getFamily());
		families.add(FontManager.getRunescapeSmallFont().getFamily());
		families.addAll(FontManager.getBuiltInFonts());
		families.addAll(FontManager.getCustomFonts());
		families.addAll(FontManager.getSystemFonts());
		if (current != null)
		{
			families.add(current);
		}
		return families.toArray(new String[0]);
	}

	private enum FontStyle
	{
		NORMAL("Normal", false, false),
		BOLD("Bold", true, false),
		ITALIC("Italic", false, true),
		BOLD_ITALIC("Bold-Italic", true, true);

		private final String label;
		private final boolean bold;
		private final boolean italic;

		FontStyle(String label, boolean bold, boolean italic)
		{
			this.label = label;
			this.bold = bold;
			this.italic = italic;
		}

		static FontStyle of(boolean bold, boolean italic)
		{
			return bold ? (italic ? BOLD_ITALIC : BOLD) : (italic ? ITALIC : NORMAL);
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private JButton colorButton(Color initial, Consumer<Color> onChange)
	{
		JButton button = new JButton();
		button.setBackground(initial);
		button.setOpaque(true);
		// A text-less button's natural size is just its margins - give it a proper swatch size.
		button.setPreferredSize(new Dimension(48, 20));
		button.setFocusPainted(false);
		button.addActionListener(e ->
		{
			Color chosen = JColorChooser.showDialog(button, "Color", button.getBackground());
			if (chosen != null)
			{
				button.setBackground(chosen);
				onChange.accept(chosen);
			}
		});
		return button;
	}

	private static JPanel labeled(String label, Component control)
	{
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.add(new JLabel(label), BorderLayout.WEST);
		row.add(control, BorderLayout.EAST);
		return row;
	}

	private static JPanel pinnedToTop(JComponent content)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(content, BorderLayout.NORTH);
		return wrapper;
	}

	/**
	 * Settings tab layout: one row per component, each at its natural height. Rows stretch only
	 * horizontally (so labels line up on the left and controls on the right) and controls keep
	 * their normal size. GridLayout, used before, made every row as tall as the tallest one and
	 * stretched every control to match.
	 */
	private static final class FormPanel extends JPanel
	{
		private int nextRow;

		FormPanel()
		{
			super(new GridBagLayout());
			setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		}

		/**
		 * Bold section title with a divider under it. Extra space above, except for the first
		 * one, separates it from the section before.
		 */
		void addHeader(String title)
		{
			JLabel label = new JLabel(title);
			label.setFont(label.getFont().deriveFont(Font.BOLD));
			JPanel header = new JPanel(new BorderLayout(0, 2));
			header.setBorder(BorderFactory.createEmptyBorder(nextRow == 0 ? 0 : 10, 0, 0, 0));
			header.add(label, BorderLayout.NORTH);
			header.add(new JSeparator(), BorderLayout.SOUTH);
			add(header);
		}

		@Override
		protected void addImpl(Component comp, Object constraints, int index)
		{
			if (constraints == null)
			{
				GridBagConstraints c = new GridBagConstraints();
				c.gridx = 0;
				c.gridy = nextRow++;
				c.weightx = 1;
				c.fill = GridBagConstraints.HORIZONTAL;
				c.anchor = GridBagConstraints.NORTHWEST;
				c.insets = new Insets(2, 0, 2, 0);
				constraints = c;
			}
			super.addImpl(comp, constraints, index);
		}
	}

	private void rebuild()
	{
		String filter = searchField.getText().trim().toLowerCase();
		for (Map.Entry<TrackedBuff.TimerCategory, DefaultListModel<TrackedBuff>> entry : models.entrySet())
		{
			entry.getValue().clear();
		}
		TrackedBuff[] sorted = TrackedBuff.values();
		Arrays.sort(sorted, Comparator.comparing(buff -> buff.getLabel().toLowerCase()));
		for (TrackedBuff buff : sorted)
		{
			if (!filter.isEmpty() && !buff.getLabel().toLowerCase().contains(filter))
			{
				continue;
			}
			DefaultListModel<TrackedBuff> model = models.get(plugin.getTimerCategory(buff));
			(model != null ? model : models.get(TrackedBuff.TimerCategory.UNMAPPED)).addElement(buff);
		}
		for (Map.Entry<TrackedBuff.TimerCategory, DefaultListModel<TrackedBuff>> entry : models.entrySet())
		{
			countLabels.get(entry.getKey()).setText(String.valueOf(entry.getValue().getSize()));
		}
		applyExpansion();
	}

	private boolean isFiltering()
	{
		return !searchField.getText().trim().isEmpty();
	}

	/**
	 * With no search text, each category shows its saved expanded/collapsed state. While
	 * searching, categories with a match open and the rest close, without touching the saved
	 * state, so clearing the search puts everything back how it was.
	 */
	private void applyExpansion()
	{
		boolean filtering = isFiltering();
		for (TrackedBuff.TimerCategory category : lists.keySet())
		{
			boolean expanded = filtering ? !models.get(category).isEmpty() : plugin.isCategoryExpanded(category);
			setExpandedVisually(category, expanded);
		}
		revalidateAll();
	}

	private void toggleExpanded(TrackedBuff.TimerCategory category)
	{
		boolean expanding = !bodies.get(category).isVisible();
		if (isFiltering())
		{
			// Temporary while searching - the next keystroke re-derives it from the matches.
			setExpandedVisually(category, expanding);
			revalidateAll();
		}
		else
		{
			plugin.setCategoryExpanded(category, expanding);
			applyExpansion();
		}
	}

	/** Enables or disables every timer currently listed in the category (search matches only, while searching). */
	private void setListedTimersEnabled(TrackedBuff.TimerCategory category, boolean enabled)
	{
		DefaultListModel<TrackedBuff> model = models.get(category);
		for (int i = 0; i < model.getSize(); i++)
		{
			plugin.setTimerEnabled(model.getElementAt(i), enabled);
		}
		lists.get(category).repaint();
	}

	/** Same rules as a single toggle: saved normally, temporary while searching. */
	private void setAllExpanded(boolean expanded)
	{
		boolean filtering = isFiltering();
		for (TrackedBuff.TimerCategory category : lists.keySet())
		{
			if (filtering)
			{
				setExpandedVisually(category, expanded);
			}
			else
			{
				plugin.setCategoryExpanded(category, expanded);
			}
		}
		if (filtering)
		{
			revalidateAll();
		}
		else
		{
			applyExpansion();
		}
	}

	private void setExpandedVisually(TrackedBuff.TimerCategory category, boolean expanded)
	{
		bodies.get(category).setVisible(expanded);
		collapseButtons.get(category).setIcon(new ArrowIcon(expanded));
	}

	private void revalidateAll()
	{
		getWrappedPanel().revalidate();
		getWrappedPanel().repaint();
	}

	/**
	 * Called when a drag begins. Snapshots the row as an image for the ghost, dims the original
	 * row, and starts following the cursor to highlight whichever category it's over.
	 */
	private void startDragFeedback(TransferHandler handler, JList<TrackedBuff> list, int index, TrackedBuff buff)
	{
		endDragFeedback(); // in case a previous drag never reported back

		Rectangle cell = list.getCellBounds(index, index);
		if (cell != null && cell.width > 0 && cell.height > 0)
		{
			// Snapshot before draggingBuff is set, so the image is the normal (not dimmed) row.
			BufferedImage image = new BufferedImage(cell.width, cell.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			Component renderer = list.getCellRenderer().getListCellRendererComponent(list, buff, index, false, false);
			rendererPane.paintComponent(g, renderer, list, 0, 0, cell.width, cell.height, true);
			g.dispose();

			// Keep the ghost where the row was grabbed, rather than snapping its corner to the cursor.
			ghostOffset = new Point(8, cell.height / 2);
			PointerInfo pointer = MouseInfo.getPointerInfo();
			if (pointer != null)
			{
				Point mouse = pointer.getLocation();
				SwingUtilities.convertPointFromScreen(mouse, list);
				if (cell.contains(mouse))
				{
					ghostOffset = new Point(mouse.x - cell.x, mouse.y - cell.y);
				}
			}

			if (DragSource.isDragImageSupported())
			{
				handler.setDragImage(image);
				handler.setDragImageOffset(new Point(-ghostOffset.x, -ghostOffset.y));
			}
			else
			{
				// No native drag images (typical on Linux) - draw our own on the window's drag layer.
				handler.setDragImage(null);
				JRootPane root = SwingUtilities.getRootPane(list);
				if (root != null)
				{
					ghost = new DragGhost(image);
					root.getLayeredPane().add(ghost, JLayeredPane.DRAG_LAYER);
					if (pointer != null)
					{
						moveGhost(pointer.getLocation());
					}
				}
			}
		}

		draggingBuff = buff;
		dragSourceCategory = targetCategory(list);
		list.repaint();
		DragSource.getDefaultDragSource().addDragSourceMotionListener(dragMotionListener);
	}

	private void onDragMoved(Point screen)
	{
		if (screen == null)
		{
			return;
		}
		moveGhost(screen);
		TrackedBuff.TimerCategory hovered = categoryAt(screen);
		// Dropping back into its own category does nothing, so don't advertise it as a target.
		setHighlightedCategory(hovered == dragSourceCategory ? null : hovered);
	}

	private void moveGhost(Point screen)
	{
		if (ghost == null || ghost.getParent() == null)
		{
			return;
		}
		Point p = new Point(screen);
		SwingUtilities.convertPointFromScreen(p, ghost.getParent());
		ghost.setLocation(p.x - ghostOffset.x, p.y - ghostOffset.y);
	}

	/** The category whose section (header or list) is under this screen point, if any is visible there. */
	private TrackedBuff.TimerCategory categoryAt(Point screen)
	{
		for (Map.Entry<TrackedBuff.TimerCategory, JPanel> entry : sections.entrySet())
		{
			JPanel section = entry.getValue();
			if (!section.isShowing())
			{
				continue;
			}
			Point p = new Point(screen);
			SwingUtilities.convertPointFromScreen(p, section);
			if (section.getVisibleRect().contains(p))
			{
				return entry.getKey();
			}
		}
		return null;
	}

	private void setHighlightedCategory(TrackedBuff.TimerCategory category)
	{
		if (category == highlightedCategory)
		{
			return;
		}
		if (highlightedCategory != null)
		{
			sections.get(highlightedCategory).setBorder(SECTION_BORDER);
		}
		highlightedCategory = category;
		if (category != null)
		{
			sections.get(category).setBorder(SECTION_DROP_BORDER);
		}
	}

	/** Called when a drag ends (dropped anywhere, or cancelled). */
	private void endDragFeedback()
	{
		DragSource.getDefaultDragSource().removeDragSourceMotionListener(dragMotionListener);
		if (ghost != null)
		{
			Container parent = ghost.getParent();
			if (parent != null)
			{
				Rectangle bounds = ghost.getBounds();
				parent.remove(ghost);
				parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
			}
			ghost = null;
		}
		setHighlightedCategory(null);
		if (draggingBuff != null)
		{
			draggingBuff = null;
			dragSourceCategory = null;
			for (JList<TrackedBuff> list : lists.values())
			{
				list.repaint();
			}
		}
	}

	/** Semi-transparent snapshot of the dragged row that follows the cursor. */
	private static final class DragGhost extends JComponent
	{
		private final BufferedImage image;

		DragGhost(BufferedImage image)
		{
			this.image = image;
			setSize(image.getWidth(), image.getHeight());
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
			g2.drawImage(image, 0, 0, null);
			g2.setColor(ColorScheme.BRAND_ORANGE);
			g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			g2.dispose();
		}
	}

	private static final class TimerTransferable implements Transferable
	{
		private final String buffName;

		TimerTransferable(String buffName)
		{
			this.buffName = buffName;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[]{TIMER_FLAVOR};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return TIMER_FLAVOR.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
			{
				throw new UnsupportedFlavorException(flavor);
			}
			return buffName;
		}
	}

	/** Timer drops go to the shared handler; everything else goes to the text field's own handler. */
	private final class TimerDropDelegatingHandler extends TransferHandler
	{
		private final TransferHandler textHandler;

		TimerDropDelegatingHandler(TransferHandler textHandler)
		{
			this.textHandler = textHandler;
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			return support.isDataFlavorSupported(TIMER_FLAVOR)
				? transferHandler.canImport(support)
				: textHandler != null && textHandler.canImport(support);
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			return support.isDataFlavorSupported(TIMER_FLAVOR)
				? transferHandler.importData(support)
				: textHandler != null && textHandler.importData(support);
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return textHandler == null ? NONE : textHandler.getSourceActions(c);
		}

		@Override
		public void exportAsDrag(JComponent comp, InputEvent e, int action)
		{
			if (textHandler != null)
			{
				textHandler.exportAsDrag(comp, e, action);
			}
		}

		@Override
		public void exportToClipboard(JComponent comp, Clipboard clip, int action)
		{
			if (textHandler != null)
			{
				textHandler.exportToClipboard(comp, clip, action);
			}
		}
	}

	/** Small filled triangle: pointing right when collapsed, down when expanded. */
	private static final class ArrowIcon implements Icon
	{
		private static final int SIZE = 10;
		private final boolean expanded;

		ArrowIcon(boolean expanded)
		{
			this.expanded = expanded;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
			Path2D.Float triangle = new Path2D.Float();
			if (expanded)
			{
				triangle.moveTo(x, y + 2);
				triangle.lineTo(x + SIZE, y + 2);
				triangle.lineTo(x + SIZE / 2f, y + SIZE - 1);
			}
			else
			{
				triangle.moveTo(x + 2, y);
				triangle.lineTo(x + SIZE - 1, y + SIZE / 2f);
				triangle.lineTo(x + 2, y + SIZE);
			}
			triangle.closePath();
			g2.fill(triangle);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/** An X, for deleting a category. */
	private static final class DeleteIcon implements Icon
	{
		private static final int SIZE = 10;

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
			g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
			g2.drawLine(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1);
			g2.drawLine(x + SIZE - 1, y + 1, x + 1, y + SIZE - 1);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/** Eight-toothed gear with a hole in the middle. */
	private static final class GearIcon implements Icon
	{
		private static final int SIZE = 12;
		private static final Area GEAR = buildGear();

		private static Area buildGear()
		{
			double center = SIZE / 2.0;
			Area gear = new Area(new Ellipse2D.Double(center - 4, center - 4, 8, 8));
			for (int i = 0; i < 8; i++)
			{
				Area tooth = new Area(new Rectangle2D.Double(center - 1.25, 0, 2.5, 3));
				tooth.transform(AffineTransform.getRotateInstance(Math.PI / 4 * i, center, center));
				gear.add(tooth);
			}
			gear.subtract(new Area(new Ellipse2D.Double(center - 1.75, center - 1.75, 3.5, 3.5)));
			return gear;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
			g2.translate(x, y);
			g2.fill(GEAR);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	/** Renders each row as a checkbox (checked = shown) with the timer's label as its text. */
	private final class TimerCheckBoxRenderer implements ListCellRenderer<TrackedBuff>
	{
		private final JCheckBox checkBox = new JCheckBox();
		private final ColorSwatch swatch = new ColorSwatch();
		private String swatchTooltip;
		/** Checkbox on the left, the timer's color swatch on the right. */
		private final JPanel row = new JPanel(new BorderLayout())
		{
			@Override
			public String getToolTipText(MouseEvent event)
			{
				// JList asks the renderer for a tooltip at the mouse position - only the swatch has one.
				return event.getX() >= getWidth() - SWATCH_HIT_WIDTH ? swatchTooltip : null;
			}
		};

		TimerCheckBoxRenderer()
		{
			row.add(checkBox, BorderLayout.CENTER);
			JPanel swatchHolder = new JPanel(new java.awt.GridBagLayout());
			swatchHolder.setOpaque(false);
			swatchHolder.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
			swatchHolder.add(swatch);
			row.add(swatchHolder, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends TrackedBuff> list, TrackedBuff value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			Component box = renderCheckBox(list, value, isSelected);
			TrackedBuff.TimerCategory listCategory = targetCategory(list);
			boolean unmapped = listCategory != null && listCategory.isUnmapped();
			ColorStyle style = unmapped || listCategory == null ? ColorStyle.INDIVIDUAL : plugin.getColorStyle(listCategory);
			// Faded when this category doesn't use individual colors, so it's clear the swatch
			// isn't what's on screen right now (it's still editable for when you switch back).
			swatch.set(plugin.getTimerColor(value), !unmapped && style == ColorStyle.INDIVIDUAL);
			swatchTooltip = unmapped ? null
				: style == ColorStyle.INDIVIDUAL ? "Click to change " + value.getLabel() + "'s color"
				: "Click to change " + value.getLabel() + "'s color (not used right now: this category is set to \""
					+ style + "\")";
			row.setBackground(box.getBackground());
			return row;
		}

		private Component renderCheckBox(JList<? extends TrackedBuff> list, TrackedBuff value, boolean isSelected)
		{
			checkBox.setText(value.getLabel());
			checkBox.setSelected(plugin.isTimerEnabled(value));
			checkBox.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
			checkBox.setForeground(value == draggingBuff ? Color.GRAY
				: isSelected ? list.getSelectionForeground() : list.getForeground());
			checkBox.setFocusPainted(false);
			TrackedBuff.TimerCategory listCategory = targetCategory(list);
			checkBox.setEnabled(listCategory == null || !listCategory.isUnmapped());
			checkBox.setOpaque(true);
			return checkBox;
		}
	}

	/** The small filled rectangle on each timer row showing (and opening the picker for) its color. */
	private static final class ColorSwatch extends JComponent
	{
		private Color color = Color.GRAY;
		private boolean active = true;

		ColorSwatch()
		{
			setPreferredSize(new Dimension(SWATCH_WIDTH, SWATCH_HEIGHT));
		}

		void set(Color color, boolean active)
		{
			this.color = color;
			this.active = active;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			if (!active)
			{
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			}
			g2.setColor(color);
			g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
			g2.setColor(Color.DARK_GRAY);
			g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			g2.dispose();
		}
	}
}
