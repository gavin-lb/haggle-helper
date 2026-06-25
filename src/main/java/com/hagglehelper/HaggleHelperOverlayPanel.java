package com.hagglehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;

import javax.inject.Inject;

import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class HaggleHelperOverlayPanel extends OverlayPanel
{
    @Inject
    private HaggleHelperConfig config;

    private Instant sessionStart;
    private int profit = 0;
    
    @Inject
    private HaggleHelperOverlayPanel()
    {
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(220, 0));
        
        getMenuEntries().add(
            new OverlayMenuEntry(
                MenuAction.RUNELITE_OVERLAY,
                "Reset",
                "Haggle Helper"
            )
        );
    }

    public void addProfit(int value) 
	{
		profit += value;
		if (sessionStart == null)
		{
			sessionStart = Instant.now();
		}
	}

	public int getProfitPerHour()
	{
		if (sessionStart == null)
		{
			return 0;
		}

		long elapsedSeconds =
			Duration.between(sessionStart, Instant.now()).getSeconds();

		if (elapsedSeconds <= 0)
		{
			return 0;
		}

		return (int) ((long) profit * 3600 / elapsedSeconds);
	}

    
    public void reset() {
        profit = 0;
		sessionStart = null;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.profitInfoEnabled())
        {
            return null;
        }

        if (profit == 0)
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(
            TitleComponent.builder()
                .text("Haggle Helper")
                .build()
        );

        panelComponent.getChildren().add(
            LineComponent.builder()
                .left("Profit")
                .right(HaggleHelperPlugin.formatGp(profit) + " gp")
                .rightColor(
                    profit > 10_000_000 
                    ? Color.decode("#00ff84") 
                    : profit > 10_000 ? Color.WHITE : Color.YELLOW
                )
                .build()
        );

        final int profitPerHour = getProfitPerHour();
        panelComponent.getChildren().add(
            LineComponent.builder()
                .left("Profit/hr")
                .right(HaggleHelperPlugin.formatGp(profitPerHour) + " gp/hr")
                .rightColor(
                    profitPerHour > 10_000_000 
                    ? Color.decode("#00ff84") 
                    : profitPerHour > 10_000 ? Color.WHITE : Color.YELLOW
                )
                .build()
        );

        return super.render(graphics);
    }
}