package com.ams.hrms.ui.dashboard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.PlotState;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.data.xy.XYDataset;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;

/**
 * Applies the application look to JFreeChart objects so charts blend into the
 * FlatLaf theme (spec sections 9 and 35). All colors resolve from
 * {@link Palette} at build time; the dashboard rebuilds charts on theme
 * switches.
 */
public final class ChartTheme {

    private static final Role[] SERIES_ROLES = {
            Role.ACCENT, Role.SUCCESS, Role.WARNING, Role.INFO, Role.DANGER
    };

    /**
     * Donut/pie schemes keyed by status name. Dark: active blue #3B82F6,
     * inactive orange #F97316, neutral gray #94A3B8. Light: bright sky
     * #3EA6FF, warm sand #E5D4B3, soft yellow #F3E1A9 and plum #8A607E.
     * Remaining slices fall through the per-theme fallback sequences.
     */
    private static final Color PIE_DARK_ACTIVE = new Color(0x3B82F6);
    private static final Color PIE_DARK_INACTIVE = new Color(0xF97316);
    private static final Color[] PIE_DARK_FALLBACK = {
            new Color(0x94A3B8), new Color(0x10B981), new Color(0xF59E0B),
            new Color(0x8B5CF6), new Color(0xEF4444)
    };
    private static final Color PIE_LIGHT_ACTIVE = new Color(0x3EA6FF);
    private static final Color PIE_LIGHT_INACTIVE = new Color(0xE5D4B3);
    private static final Color[] PIE_LIGHT_FALLBACK = {
            new Color(0xF3E1A9), new Color(0x8A607E), new Color(0x10B981),
            new Color(0xF59E0B), new Color(0xEF4444)
    };

    private ChartTheme() {
    }

    /** Color for the n-th series, cycling through the semantic palette. */
    public static Color seriesColor(int index) {
        return Palette.color(SERIES_ROLES[index % SERIES_ROLES.length]);
    }

    /**
     * Bar fill: lighter sky #60A5FA on dark surfaces; a steel-to-sky blue
     * blend on light surfaces.
     */
    public static Color barColor() {
        if (Palette.isDarkUi()) {
            return new Color(0x60A5FA);
        }
        return com.ams.hrms.util.UiGraphics.blend(
                new Color(0x94A3B8), new Color(0x60A5FA), 0.45);
    }

    /**
     * Slice color for a pie/donut section. Recognized status names keep
     * stable colors regardless of data order; unknown names fall back to
     * the neutral-first fallback sequence of the active theme.
     */
    public static Color pieSliceColor(String label, int fallbackIndex) {
        String normalized = label == null ? "" : label.trim().toLowerCase();
        boolean dark = Palette.isDarkUi();
        if (normalized.startsWith("active")) {
            return dark ? PIE_DARK_ACTIVE : PIE_LIGHT_ACTIVE;
        }
        if (normalized.startsWith("inactive")) {
            return dark ? PIE_DARK_INACTIVE : PIE_LIGHT_INACTIVE;
        }
        Color[] fallback = dark ? PIE_DARK_FALLBACK : PIE_LIGHT_FALLBACK;
        return fallback[Math.floorMod(fallbackIndex, fallback.length)];
    }

    private static Color surface() {
        return Palette.color(Role.CARD_BG);
    }

    private static Color grid() {
        return Palette.color(Role.CARD_BORDER);
    }

    private static Color muted() {
        return Palette.color(Role.TEXT_MUTED);
    }

    private static Font smallFont() {
        Font base = javax.swing.UIManager.getFont("defaultFont");
        if (base == null) {
            base = new javax.swing.JLabel().getFont();
        }
        return base.deriveFont(Font.PLAIN, 11f);
    }

    /** Shared styling: backgrounds, fonts, no borders. */
    private static void applyBase(JFreeChart chart) {
        chart.setBackgroundPaint(surface());
        chart.setBorderVisible(false);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(smallFont());
            chart.getLegend().setBackgroundPaint(surface());
            chart.getLegend().setItemPaint(muted());
        }
    }

    private static void styleCategoryAxes(CategoryPlot plot) {
        plot.setBackgroundPaint(surface());
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(grid());
        plot.getDomainAxis().setTickLabelFont(smallFont());
        plot.getDomainAxis().setTickLabelPaint(muted());
        plot.getDomainAxis().setAxisLinePaint(grid());
        plot.getDomainAxis().setLabelFont(smallFont());
        plot.getRangeAxis().setTickLabelFont(smallFont());
        plot.getRangeAxis().setTickLabelPaint(muted());
        plot.getRangeAxis().setAxisLineVisible(false);
        plot.getRangeAxis().setLabelFont(smallFont());
    }

    /** Flat bar chart with a single series. */
    public static JFreeChart barChart(String title, CategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart(
                null, null, null, dataset, PlotOrientation.VERTICAL, false, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, barColor());
        renderer.setMaximumBarWidth(0.12);

        // Value labels above each bar (reference design)
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelFont(smallFont().deriveFont(Font.BOLD, 11f));
        renderer.setDefaultItemLabelPaint(Palette.color(Role.TEXT));
        renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));

        useIntegerTicks(plot, dataset);
        styleCategoryAxes(plot);
        applyBase(chart);
        return chart;
    }

    /** Multi-series bar chart (e.g. leave usage). */
    public static JFreeChart multiBarChart(CategoryDataset dataset, boolean legend) {
        JFreeChart chart = ChartFactory.createBarChart(
                null, null, null, dataset, PlotOrientation.VERTICAL, legend, true, false);
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        for (int i = 0; i < dataset.getRowCount(); i++) {
            renderer.setSeriesPaint(i, seriesColor(i));
        }
        renderer.setMaximumBarWidth(0.12);
        if (dataset.getRowCount() == 1) {
            useIntegerTicks(plot, dataset);
        } else {
            useOneDecimalTicks(plot, dataset);
        }
        styleCategoryAxes(plot);
        applyBase(chart);
        return chart;
    }

    /** Whole-number ticks for count-style axes, sized from the data. */
    private static void useIntegerTicks(CategoryPlot plot, CategoryDataset dataset) {
        double max = maxValue(dataset);
        int unit = Math.max(1, (int) Math.ceil(max / 5));
        if (plot.getRangeAxis() instanceof org.jfree.chart.axis.NumberAxis axis) {
            axis.setNumberFormatOverride(java.text.NumberFormat.getIntegerInstance());
            axis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(unit));
        }
    }

    /** Half/whole-day ticks for day-count axes. */
    private static void useOneDecimalTicks(CategoryPlot plot, CategoryDataset dataset) {
        double max = maxValue(dataset);
        double unit = Math.max(0.5, Math.ceil(max / 4 * 2) / 2.0);
        if (plot.getRangeAxis() instanceof org.jfree.chart.axis.NumberAxis axis) {
            axis.setNumberFormatOverride(new java.text.DecimalFormat("#.#"));
            axis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(unit));
        }
    }

    private static double maxValue(CategoryDataset dataset) {
        double max = 0;
        for (int row = 0; row < dataset.getRowCount(); row++) {
            for (int column = 0; column < dataset.getColumnCount(); column++) {
                Number value = dataset.getValue(row, column);
                if (value != null && value.doubleValue() > max) {
                    max = value.doubleValue();
                }
            }
        }
        return max;
    }

    /** Pie chart with the status color scheme, legend, and no shadow. */
    public static JFreeChart pieChart(org.jfree.data.general.PieDataset dataset) {
        JFreeChart chart = ChartFactory.createPieChart(null, dataset, true, true, false);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(surface());
        plot.setOutlineVisible(false);
        plot.setLabelGenerator(null);
        plot.setShadowGenerator(null);
        plot.setShadowPaint(new Color(0, 0, 0, 0));
        plot.setShadowXOffset(0);
        plot.setShadowYOffset(0);
        plot.setLabelFont(smallFont());
        plot.setDefaultSectionOutlinePaint(surface());
        for (int i = 0; i < dataset.getItemCount(); i++) {
            Comparable<?> key = dataset.getKey(i);
            plot.setSectionPaint(key, pieSliceColor(String.valueOf(key), i));
        }
        applyBase(chart);
        return chart;
    }

    /**
     * Donut chart for employee status (reference design): percent labels on
     * the ring (empty sections get no label), no built-in legend - the
     * dashboard renders its own legend with counts - and a "Total N" caption
     * in the donut hole.
     */
    public static JFreeChart donutChart(PieDataset dataset) {
        CenterTextRingPlot plot = new CenterTextRingPlot(dataset);
        JFreeChart chart = new JFreeChart(plot);
        chart.setBackgroundPaint(surface());
        chart.setBorderVisible(false);
        // JFreeChart auto-attaches a white-backed legend here; the dashboard
        // renders its own count legend beside the donut instead.
        chart.removeLegend();

        plot.setBackgroundPaint(surface());
        plot.setOutlineVisible(false);
        plot.setShadowGenerator(null);
        plot.setStartAngle(90);
        plot.setInteriorGap(0.10);
        plot.setSectionDepth(0.42);
        plot.setSeparatorsVisible(false);
        plot.setSectionOutlinesVisible(false);
        plot.setSimpleLabels(true);
        plot.setLabelFont(smallFont());
        plot.setLabelPaint(muted());
        // Theme-aware chip behind each percent label - JFreeChart's default
        // is a semi-opaque white box that glares on the dark surface.
        Color chip = surface();
        plot.setLabelBackgroundPaint(
                new Color(chip.getRed(), chip.getGreen(), chip.getBlue(), 170));
        plot.setLabelOutlinePaint(new Color(0, 0, 0, 0));
        plot.setLabelShadowPaint(new Color(0, 0, 0, 0));

        double total = 0;
        for (int i = 0; i < dataset.getItemCount(); i++) {
            Comparable<?> key = dataset.getKey(i);
            Number value = dataset.getValue(key);
            if (value != null) {
                total += value.doubleValue();
            }
            plot.setSectionPaint(key, pieSliceColor(String.valueOf(key), i));
        }
        final double totalCount = total;
        plot.setLabelGenerator(new PieSectionLabelGenerator() {
            @Override
            public String generateSectionLabel(PieDataset ds, Comparable key) {
                Number value = ds.getValue(key);
                if (value == null || value.doubleValue() <= 0 || totalCount <= 0) {
                    return null;
                }
                return Math.round(value.doubleValue() / totalCount * 100) + "%";
            }

            @Override
            public java.text.AttributedString generateAttributedSectionLabel(
                    PieDataset ds, Comparable key) {
                return null;
            }
        });
        return chart;
    }

    /** Ring plot that paints a "Total N" caption in the donut hole. */
    private static final class CenterTextRingPlot extends RingPlot {

        private final double total;

        CenterTextRingPlot(PieDataset dataset) {
            super(dataset);
            double sum = 0;
            for (int i = 0; i < dataset.getItemCount(); i++) {
                Number value = dataset.getValue(i);
                if (value != null) {
                    sum += value.doubleValue();
                }
            }
            this.total = sum;
        }

        @Override
        public void draw(Graphics2D g2, Rectangle2D area, Point2D anchor,
                PlotState parentState, PlotRenderingInfo info) {
            super.draw(g2, area, anchor, parentState, info);
            float cx = (float) area.getCenterX();
            float cy = (float) area.getCenterY();

            g2.setPaint(muted());
            g2.setFont(smallFont());
            String caption = "Total";
            g2.drawString(caption, cx - g2.getFontMetrics().stringWidth(caption) / 2, cy - 6);

            g2.setPaint(Palette.color(Role.TEXT));
            g2.setFont(smallFont().deriveFont(Font.BOLD, 17f));
            String value = String.valueOf((long) total);
            g2.drawString(value, cx - g2.getFontMetrics().stringWidth(value) / 2, cy + 15);
        }
    }

    /** Time-series line chart (attendance trend, payroll cost). */
    public static JFreeChart lineChart(XYDataset dataset, boolean legend) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, null, null, dataset, legend, true, false);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(surface());
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(grid());
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setDefaultShapesVisible(false);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, seriesColor(i));
        }
        plot.getDomainAxis().setTickLabelFont(smallFont());
        plot.getDomainAxis().setTickLabelPaint(muted());
        plot.getDomainAxis().setAxisLinePaint(grid());
        plot.getRangeAxis().setTickLabelFont(smallFont());
        plot.getRangeAxis().setTickLabelPaint(muted());
        plot.getRangeAxis().setAxisLineVisible(false);
        applyBase(chart);
        return chart;
    }

    /**
     * Wraps a chart in a non-interactive ChartPanel matching the card surface
     * at the standard dashboard chart size, which fits the fixed card-body
     * viewport so charts render fully without inner scroll bars.
     */
    public static ChartPanel wrap(JFreeChart chart) {
        return wrap(chart, CHART_PREFERRED_WIDTH, CHART_PREFERRED_HEIGHT);
    }

    /**
     * Wraps a chart in a non-interactive ChartPanel matching the card surface
     * at an explicit preferred size. Sizes at or below the card-body viewport
     * let the body track (fill) the viewport; anything larger falls back to
     * inner scroll bars.
     */
    public static ChartPanel wrap(JFreeChart chart, int width, int height) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(surface());
        panel.setMouseWheelEnabled(false);
        panel.setDomainZoomable(false);
        panel.setRangeZoomable(false);
        panel.setPreferredSize(new java.awt.Dimension(width, height));
        return panel;
    }

    /**
     * Small titled section used above each chart inside its card. The chart
     * body sits in a fixed-height scroll pane with permanently visible
     * vertical and horizontal scroll bars; charts sized to the body render
     * fully, and the bars double as a fallback when the window is too small.
     */
    public static JPanel sectionCard(String title, JComponent content) {
        JPanel card = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 1, insets 16 18 14 18, gapx 8, gapy 10",
                "[grow,fill]", "[][][grow,fill]"));
        card.setBackground(Palette.color(Role.CARD_BG));
        javax.swing.JLabel titleLabel = new javax.swing.JLabel(title.toUpperCase());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setForeground(muted());
        card.add(titleLabel);

        JScrollPane scroll = new JScrollPane(new ScrollableBody(content));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.setBackground(surface());
        scroll.getViewport().setBackground(surface());
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        card.add(scroll, "height " + BODY_VIEWPORT_HEIGHT + "!");
        return card;
    }

    /** Fixed height of every chart card's scrollable body. */
    public static final int BODY_VIEWPORT_HEIGHT = 230;

    /**
     * Standard chart width: fits a half-width card body at the 1180px
     * minimum window size, so charts scale with the card instead of
     * scrolling inside it.
     */
    public static final int CHART_PREFERRED_WIDTH = 500;

    /** Standard chart height, just inside the fixed body viewport. */
    public static final int CHART_PREFERRED_HEIGHT = BODY_VIEWPORT_HEIGHT - 6;

    /** Donut width sized to share its card with the status legend. */
    public static final int DONUT_PREFERRED_WIDTH = 300;

    /**
     * Scroll-pane body for chart sections. The body tracks (fills) the
     * viewport in a dimension whenever the content fits - the normal case
     * for the standard chart size - so scroll bars only appear as a
     * fallback when the window shrinks below the chart's preferred size.
     */
    private static final class ScrollableBody extends JPanel implements Scrollable {

        /** Narrowest usable chart width before a card scrolls sideways. */
        private static final int MIN_BODY_WIDTH = 300;

        /**
         * Viewports a hair smaller than the preferred size (fractional-DPI
         * rounding) still track instead of showing a 1px scroll bar.
         */
        private static final int TRACK_TOLERANCE_PX = 2;

        private final JComponent body;

        ScrollableBody(JComponent body) {
            super(new BorderLayout());
            this.body = body;
            add(body, BorderLayout.CENTER);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension pref = body.getPreferredSize();
            Dimension min = body.getMinimumSize();
            int minWidth = min == null ? 0 : min.width;
            int minHeight = min == null ? 0 : min.height;
            return new Dimension(
                    Math.max(pref.width, Math.max(MIN_BODY_WIDTH, minWidth)),
                    Math.max(pref.height, minHeight));
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            JViewport viewport = viewport();
            return viewport == null || getPreferredSize().width
                    <= viewport.getWidth() + TRACK_TOLERANCE_PX;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            JViewport viewport = viewport();
            return viewport == null || getPreferredSize().height
                    <= viewport.getHeight() + TRACK_TOLERANCE_PX;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation,
                int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation,
                int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        private JViewport viewport() {
            Container parent = getParent();
            return parent instanceof JViewport jv ? jv : null;
        }
    }
}
