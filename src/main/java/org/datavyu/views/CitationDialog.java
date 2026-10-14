package org.datavyu.views;

import org.datavyu.util.Citation;
import org.datavyu.util.DatavyuVersion;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Shows how to cite Watavyu, in APA and BibTeX, each with a button that copies it to the clipboard.
 */
public class CitationDialog extends JDialog {

    public CitationDialog(Frame parent) {
        super(parent, "Cite Watavyu", true);
        setName(getClass().getSimpleName());

        Citation citation = Citation.load();
        String version = DatavyuVersion.getVersion();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel message = new JLabel(citation.getMessage());
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(message);
        content.add(Box.createVerticalStrut(12));
        content.add(section("APA", citation.apa(version), false));
        content.add(Box.createVerticalStrut(12));
        content.add(section("BibTeX", citation.bibtex(version), true));

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(close);
        content.add(Box.createVerticalStrut(12));
        content.add(buttons);

        setContentPane(content);
        getRootPane().setDefaultButton(close);
        pack();
        setLocationRelativeTo(parent);
    }

    private static JPanel section(String name, String text, boolean monospaced) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(!monospaced);
        area.setWrapStyleWord(true);
        area.setColumns(60);
        area.setRows(monospaced ? text.split("\n").length : 3);
        if (monospaced) {
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        }
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            copy.setText("Copied");
        });

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel(name), BorderLayout.WEST);
        header.add(copy, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }
}
