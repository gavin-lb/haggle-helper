package com.hagglehelper;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

@Slf4j
public class ErrorPopups
{
	@Inject
	HaggleHelperPlugin plugin;

	@Inject
	Client client;

	@Inject
	ItemManager itemManager;

	private static String BASE_REPORT;
	private static final String BASE_EXPLANATION = "Please help improve Haggle Helper by reporting this issue " +
		"on GitHub with the \"Open GitHub Issue\" button below. Alternatively, you can click \"Copy " +
		"Report\" and paste it into a new GitHub issue manually.";
	private static final String FOOTER = "(This popup can be disabled with the \"Error reports\" config option)";

	ErrorPopups()
	{
		BASE_REPORT = String.format(
			"## Haggle Helper Bug Report%n%n" +
				" - **RuneLite version:** %s%n" +
				" - **Plugin version:** %s%n",
			RuneLiteProperties.getVersion(),
			HaggleHelperPlugin.getVersion()
		);
	}

	private void base(String extraReport, String name, String githubTitle)
	{
		final String report = BASE_REPORT + extraReport;
		final String explanation = "Haggle Helper has detected a " + name + "!\n\n" +
			BASE_EXPLANATION;

		SwingUtilities.invokeLater(() ->
		{
			JTextArea infoArea = new JTextArea(explanation);
			infoArea.setEditable(false);
			infoArea.setOpaque(false);
			infoArea.setLineWrap(true);
			infoArea.setWrapStyleWord(true);
			infoArea.setFocusable(false);
			infoArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

			JTextArea reportArea = new JTextArea(report);
			reportArea.setEditable(false);
			reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			reportArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			reportArea.setLineWrap(true);
			reportArea.setWrapStyleWord(true);
			reportArea.setFocusable(true);

			JTextArea footerArea = new JTextArea(FOOTER);
			footerArea.setEditable(false);
			footerArea.setOpaque(false);
			footerArea.setLineWrap(true);
			footerArea.setWrapStyleWord(true);
			footerArea.setFocusable(false);
			footerArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 20, 10));

			JDialog dialog = new JDialog((Frame) null, "Haggle Helper - " + name, false);

			JButton copyButton = new JButton("Copy Report");
			copyButton.addActionListener(e ->
			{
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new StringSelection(report), null);
				dialog.dispose();
			});

			String issueUrl = "https://github.com/gavin-lb/haggle-helper/issues/new" +
				"?title=" + URLEncoder.encode(
					githubTitle,
					StandardCharsets.UTF_8
				) +
				"&body=" + URLEncoder.encode(
					report,
					StandardCharsets.UTF_8
				);

			JButton githubButton = new JButton("Open GitHub Issue");
			githubButton.setBackground(ColorScheme.BRAND_ORANGE);
			githubButton.setForeground(ColorScheme.DARK_GRAY_COLOR);
			githubButton.addActionListener(e ->
			{
				LinkBrowser.browse(issueUrl);
				dialog.dispose();
			});

			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			buttons.add(copyButton);
			buttons.add(githubButton);

			JPanel panel = new JPanel(new BorderLayout(0, 10));
			panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

			JScrollPane scrollPane = new JScrollPane(reportArea);

			scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
			scrollPane.setPreferredSize(new Dimension(600, 250));

			JPanel southPanel = new JPanel(new BorderLayout());
			southPanel.add(footerArea, BorderLayout.NORTH);
			southPanel.add(buttons, BorderLayout.SOUTH);

			panel.add(infoArea, BorderLayout.NORTH);
			panel.add(scrollPane, BorderLayout.CENTER);
			panel.add(southPanel, BorderLayout.SOUTH);

			URL iconUrl = getClass().getResource("/com/hagglehelper/icons/icon.png");
			if (iconUrl != null)
			{
				dialog.setIconImage(new ImageIcon(iconUrl).getImage());
			}

			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.setContentPane(panel);
			dialog.pack();
			dialog.setMinimumSize(new Dimension(400, 300));
			dialog.setLocationRelativeTo(client.getCanvas());
			dialog.setVisible(true);
		});
	}

	public void priceMismatch(HighlightedItem item, String itemName, int value, int price, String mode)
	{
		final String name = "Price Mismatch";

		final int stock = plugin.shop.getStock(item.id);
		final String report = String.format(
			" - **Type:** %s%n" +
				"--- %n" +
				" - **Item name:** %s%n" +
				" - **Item ID:** %d%n" +
				" - **Shop name:** %s%n" +
				" - **Shop ID:** %d%n" +
				" - **Mode:** %s%n" +
				" - **Current stock:** %d%n" +
				" - **Expected price:** %d%n" +
				" - **Observed value:** %d%n",
			name,
			itemName,
			item.id,
			plugin.shop.name,
			plugin.shop.containerId,
			mode,
			stock,
			price,
			value
		);

		final String githubTitle = String.format(
			"[%s] \"%s\" %s \"%s\"",
			name, itemName, mode, plugin.shop.name
		);

		// Check if stock is unchanged after short delay to not false positive when value + sell is spammed
		Timer timer = new Timer(100, e ->
		{
			int newStock = plugin.shop.getStock(item.id);
			if (newStock == stock)
			{
				base(report, name, githubTitle);
			}
			else
			{
				log.debug(
					"Stock changed - cancelling price mismatch error popup, oldStock={} newStock={}",
					stock, newStock
				);
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	public void unknownShop(int containerId, String shopName, Item[] items)
	{
		final String name = "Unknown Shop";

		final String report = String.format(
			" - **Type:** %s%n" +
				"--- %n" +
				" - **Shop name:** %s%n" +
				" - **Shop ID:** %d%n" +
				" - **Items:** %s%n",
			name,
			shopName,
			containerId,
			Arrays.stream(items).collect(Collectors.toMap(
				item -> String.format(
					"\"%s\"",
					itemManager.getItemComposition(item.getId()).getName()
				),
				item -> item.toString()
			)));


		final String githubTitle = String.format(
			"[%s] \"%s\"",
			name, shopName
		);

		base(report, name, githubTitle);
	}
}
