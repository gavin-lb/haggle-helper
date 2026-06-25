package com.hagglehelper;

import com.google.inject.Inject;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;


@Slf4j
public class HaggleHelperPanel extends PluginPanel {
    @Inject
    private ItemManager itemManager;
 
    @Inject
    private TrackedItemsManager trackedItemsManager;

    @Inject
    private HighlightedItemsManager highlightedItemsManager;

    @Inject
	private HaggleHelperConfig config;

    @Inject
    private ClientThread clientThread;

    private static final int ROW_HEIGHT = 40;

    private final JTextField searchField = new JTextField();
    private final JTextField costField = new JTextField();
    private final JButton addButton = new JButton("Add / Update");
    private final JLabel searchFeedbackLabel = new JLabel("");
    private final JLabel costFeedbackLabel = new JLabel("");
    private final JPanel listPanel = new JPanel();

    private int resolvedItemId = -1;
    private String resolvedItemName = "";

    @Inject
    public HaggleHelperPanel()
    {
        super(false);

        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                onSearchChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                onSearchChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                onSearchChanged();
            }
        });

        searchField.addActionListener(e -> onAddClicked());
        addButton.addActionListener(e -> onAddClicked());
        costField.addActionListener(e -> onAddClicked());
    }

    public void init() {
        removeAll();

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel(String.format("<html><body style = 'color:white'>Haggle Helper <span style = 'color:#a5a5a5'>v%s</span></body></html>", HaggleHelperPlugin.VERSION));
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(Color.WHITE);
        title.setBorder(new EmptyBorder(0, 0, 4, 0));
        add(title, BorderLayout.NORTH);

        JPanel centre = new JPanel(new BorderLayout(0, 8));
        centre.setBackground(ColorScheme.DARK_GRAY_COLOR);

        centre.add(buildAddForm(), BorderLayout.NORTH);
        centre.add(buildListSection(), BorderLayout.CENTER);

        add(centre, BorderLayout.CENTER);
        
        refreshList();        
        revalidate();
        repaint();
    }

    public void deinit() {
    }

    private JPanel buildAddForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(8, 8, 8, 8))
        );

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 2, 2, 2);

        // Row 0: section label
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        JLabel sectionLabel = new JLabel("Add an item");
        sectionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sectionLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        panel.add(sectionLabel, c);

        // Row 1: search field
        c.gridy = 1;
        c.gridwidth = 2;
        searchField.setToolTipText("Enter item name");
        searchField.putClientProperty("JTextField.placeholderText", "Enter item name...");
        searchField.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(new EmptyBorder(4, 6, 4, 6));
        panel.add(searchField, c);

        // Row 2: search feedback label
        c.gridy = 2;
        searchFeedbackLabel.setFont(FontManager.getRunescapeSmallFont());
        searchFeedbackLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(searchFeedbackLabel, c);

        // Row 3: cost label + field
        c.gridy = 3;
        c.gridwidth = 1;
        c.weightx = 0;
        JLabel costLabel = new JLabel("Cost (gp):");
        costLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        costLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(costLabel, c);

        c.gridx = 1;
        c.weightx = 1;
        costField.setToolTipText("Enter item cost in gp");
        costField.putClientProperty("JTextField.placeholderText", "Enter item cost...");
        costField.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        costField.setForeground(Color.WHITE);
        costField.setCaretColor(Color.WHITE);
        costField.setBorder(new EmptyBorder(4, 6, 4, 6));
        panel.add(costField, c);

        // Row 4: cost feedback label
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0;
        c.gridwidth = 2;
        costFeedbackLabel.setFont(FontManager.getRunescapeSmallFont());
        costFeedbackLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(costFeedbackLabel, c);

        // Row 5: add button
        c.gridy = 5;
        c.weightx = 1;
        addButton.setBackground(ColorScheme.BRAND_ORANGE);
        addButton.setForeground(Color.WHITE);
        addButton.setFocusPainted(false);
        addButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        addButton.setBorder(new EmptyBorder(6, 10, 6, 10));
        panel.add(addButton, c);

        return panel;
    }

    private JScrollPane buildListSection() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        scroll.setPreferredSize(new Dimension(0, 300));
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        return scroll;
    }

    private void onSearchChanged()
    {
        resolvedItemId = -1;
        resolvedItemName = "";
        searchFeedbackLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        searchFeedbackLabel.setText("");
 
        String text = searchField.getText().trim();
        if (text.isEmpty())
        {
            return;
        }
 
        List<ItemPrice> result = itemManager.search(text);
        if (result.size() == 1)
        {
            handleItemResolved(result.get(0));
        }
        else if (result.size() > 1)
        {
            for (ItemPrice item : result)
            {
                if (item.getName().equalsIgnoreCase(text))
                {
                    handleItemResolved(item);
                    return;
                }
            }
        }
        else
        {
            searchFeedbackLabel.setForeground(new Color(220, 50, 50));
            searchFeedbackLabel.setText("✗ No matches found");
        }
    }

    private void handleItemResolved(ItemPrice item)
    {
        resolvedItemId = item.getId();
        resolvedItemName = item.getName();
        searchFeedbackLabel.setForeground(new Color(0, 200, 80));
        searchFeedbackLabel.setText("✔ " + resolvedItemName);

        if (config.autoCost()) 
        {
            costField.setText(String.valueOf(item.getWikiPrice()));
        }
    }

    private void onAddClicked()
    {
        // Validate item
        if (resolvedItemId < 0)
            {
            costFeedbackLabel.setText("");
            searchFeedbackLabel.setForeground(new Color(220, 50, 50));
            searchFeedbackLabel.setText("✗ Enter a valid item name");
            return;
        }
 
        // Validate cost
        int cost;
        try
        {
            String raw = costField.getText().trim().replace(",", "");
            cost = Integer.parseInt(raw);
            if (cost < 0) throw new NumberFormatException();
        }
        catch (NumberFormatException e)
        {
            costFeedbackLabel.setForeground(new Color(220, 50, 50));
            costFeedbackLabel.setText("✗ Enter a valid cost ≥ 0");
            return;
        }
 
        trackedItemsManager.setCost(resolvedItemId, resolvedItemName, cost);
        
        searchFeedbackLabel.setForeground(new Color(0, 200, 80));
        costFeedbackLabel.setForeground(new Color(0, 200, 80));
        costFeedbackLabel.setText("");
        searchField.setText("");
        costField.setText("");
        resolvedItemId = -1;
        resolvedItemName = "";
        refreshList();
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    void refreshList()
    {
        clientThread.invoke(() -> {
            TrackedItem[] items = trackedItemsManager.getTrackedItems();
            SwingUtilities.invokeLater(() ->
            {
                listPanel.removeAll();
     
                if (items.length == 0)
                {
                    JLabel empty = new JLabel("No items added yet.");
                    empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    empty.setFont(FontManager.getRunescapeSmallFont());
                    empty.setBorder(new EmptyBorder(8, 8, 8, 8));
                    listPanel.add(empty);
                }
                else
                {
                    for (TrackedItem ti : items)
                    {
                        listPanel.add(buildItemRow(ti));
                    }
                }
     
                listPanel.revalidate();
                listPanel.repaint();
                highlightedItemsManager.clear();
            });
        });
    }

    private JPanel buildItemRow(TrackedItem ti)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            new EmptyBorder(4, 6, 4, 6))
        );
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
 
        // Item icon
        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(32, 32));
        AsyncBufferedImage img = itemManager.getImage(ti.getItemId());
        img.addTo(icon);
        row.add(icon, BorderLayout.WEST);
 
        // Centre: name + cost
        JPanel centre = new JPanel(new GridLayout(2, 1));
        centre.setBackground(ColorScheme.DARKER_GRAY_COLOR);
 
        JLabel nameLabel = new JLabel(ti.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        centre.add(nameLabel);
 
        JLabel costLabel = new JLabel("Cost: " + HaggleHelperPlugin.formatGp(ti.getCost()) + " gp");
        costLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        costLabel.setFont(FontManager.getRunescapeSmallFont());
        centre.add(costLabel);
 
        row.add(centre, BorderLayout.CENTER);
 
        // Remove button
        JButton remove = new JButton("✕");
        remove.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        remove.setForeground(new Color(220, 50, 50));
        remove.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        remove.setBorderPainted(false);
        remove.setFocusPainted(false);
        remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        remove.addActionListener(e ->
        {
            trackedItemsManager.removeItem(ti.getItemId());
            refreshList();
        });
        row.add(remove, BorderLayout.EAST);
 
        return row;
    }
}