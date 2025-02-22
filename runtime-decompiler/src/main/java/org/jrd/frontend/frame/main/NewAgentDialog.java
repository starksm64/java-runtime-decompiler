package org.jrd.frontend.frame.main;

import org.jrd.backend.core.agentstore.AgentLiveliness;
import org.jrd.backend.core.agentstore.AgentLoneliness;
import org.jrd.backend.data.VmInfo;
import org.jrd.frontend.frame.main.decompilerview.BytecodeDecompilerView;
import org.jrd.frontend.utility.ScreenFinder;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.GridLayout;

public final class NewAgentDialog extends JDialog {

    private static final NewAgentDialog NAD = NewAgentDialog.create();
    private JList<VmInfo> vmsList;
    private JButton selectButton;

    public static void show(JList<VmInfo> vms) {
        NAD.setVms(vms);
        NAD.setVisible(true);
    }

    private NewAgentDialog() {
    }

    private void setVms(JList l) {
        this.vmsList = l;
    }

    private static NewAgentDialog create() {
        NewAgentDialog nad = new NewAgentDialog();
        nad.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        nad.setModal(false);
        JPanel buttonsPanel = new JPanel(new GridLayout(2, 4));
        ButtonGroup liveliness = new ButtonGroup();
        addLivelinessButtons(buttonsPanel, liveliness);
        JTextField portField = new JTextField();
        portField.setToolTipText(
                BytecodeDecompilerView.styleTooltip() + "Type number to enforce agent port<br>leave empty for default guessing"
        );
        buttonsPanel.add(portField);
        ButtonGroup loneliness = new ButtonGroup();
        addLonelinessButtons(buttonsPanel, loneliness);
        nad.add(buttonsPanel);
        JPanel vmPanel = new JPanel();
        vmPanel.add(new JLabel("Attach to pid:"), BorderLayout.WEST);
        JTextField pidField = new JTextField("????????");
        vmPanel.add(pidField, BorderLayout.CENTER);
        nad.selectButton = new JButton("Select");
        nad.selectButton.addActionListener(a -> {
            if (nad.vmsList == null || nad.vmsList.getSelectedValue() == null) {
                pidField.setText("?" + pidField.getText() + "?");
            } else {
                pidField.setText(nad.vmsList.getSelectedValue().getVmId());
            }
        });
        vmPanel.add(nad.selectButton, BorderLayout.EAST);
        nad.add(vmPanel, BorderLayout.NORTH);
        JPanel okCancelPanel = new JPanel();
        JButton attach = new JButton("Attach");
        attach.addActionListener(a -> {
            JOptionPane.showMessageDialog(nad, "why failed or port");
            nad.setVisible(false);
        });
        okCancelPanel.add(attach, BorderLayout.WEST);
        okCancelPanel.add(new JButton("Reset to Defaults"), BorderLayout.CENTER);
        JButton hide = new JButton("Hide");
        hide.addActionListener(a -> nad.setVisible(false));
        okCancelPanel.add(hide, BorderLayout.EAST);
        nad.add(okCancelPanel, BorderLayout.SOUTH);
        nad.pack();
        ScreenFinder.centerWindowToCurrentScreen(nad);
        return nad;
    }

    private static void addLonelinessButtons(JPanel buttonsPanel, ButtonGroup loneliness) {
        for (AgentLoneliness al : AgentLoneliness.values()) {
            JToggleButton t = new JToggleButton(al.toButton());
            loneliness.add(t);
            buttonsPanel.add(t);
            t.setToolTipText(BytecodeDecompilerView.styleTooltip() + al.toHelp());
            if (al.equals(AgentLoneliness.SINGLE_INSTANCE)) {
                t.setSelected(true);
            }
        }
    }

    private static void addLivelinessButtons(JPanel buttonsPanel, ButtonGroup liveliness) {
        for (AgentLiveliness al : AgentLiveliness.values()) {
            JToggleButton t = new JToggleButton(al.toButton());
            liveliness.add(t);
            buttonsPanel.add(t);
            t.setToolTipText(BytecodeDecompilerView.styleTooltip() + al.toHelp());
            if (al.equals(AgentLiveliness.PERMANENT)) {
                t.setSelected(true);
            }
            if (al.equals(AgentLiveliness.ONE_SHOT)) {
                t.setEnabled(false);
                t.setToolTipText(t.getToolTipText() + "<br>Although this agent exists for CLI, have no reason for gui");
            }
        }
    }
}
