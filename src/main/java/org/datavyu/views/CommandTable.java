package org.datavyu.views;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One action for each command, which menus, shortcuts and the controls drawer share. A command's label, check mark and
 * whether it can run are worked out from the application's state, such as the selected tab's, each time the table
 * is refreshed: after a command runs, when a tab is selected or an edit made, when a menu opens and before a shortcut
 * runs. So no part of the window shows another tab's state. Nothing is worked out until the first refresh, so commands
 * can be defined before the state they read exists.
 */
final class CommandTable {

    private final Map<Command, CommandAction> actions = new EnumMap<>(Command.class);

    /**
     * Defines what a command does.
     *
     * @param command the command
     * @param run     what it does
     * @return its action, to give it state
     * @throws IllegalStateException if the command is already defined
     */
    CommandAction define(final Command command, final Runnable run) {
        CommandAction action = new CommandAction(command, run, this::refresh);
        if (actions.putIfAbsent(command, action) != null) {
            throw new IllegalStateException(command + " is defined twice");
        }
        return action;
    }

    /**
     * Returns a command's action.
     *
     * @param command the command
     * @return its action
     * @throws IllegalArgumentException if the command is not defined
     */
    Action get(final Command command) {
        CommandAction action = actions.get(command);
        if (action == null) {
            throw new IllegalArgumentException(command + " is not defined");
        }
        return action;
    }

    /** Works out every command's label, check mark and whether it can run, from the state now. */
    void refresh() {
        actions.values().forEach(CommandAction::refresh);
    }

    /** A command's action, with the state it shows. */
    static final class CommandAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final transient Runnable run;
        /** Refreshes the whole table, since a command can change any command's state. */
        private final transient Runnable afterRun;
        private transient Supplier<String> label;
        private transient BooleanSupplier checked;
        private transient BooleanSupplier enabled;

        private CommandAction(final Command command, final Runnable run, final Runnable afterRun) {
            super(command.label());
            this.run = run;
            this.afterRun = afterRun;
        }

        /**
         * Gives the command a label that changes, such as Undo's, which names the edit.
         *
         * @param label the label now
         * @return this action
         */
        CommandAction labelled(final Supplier<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Makes the command a check box, such as a mode that is on or off.
         *
         * @param checked whether it is on now
         * @return this action
         */
        CommandAction checked(final BooleanSupplier checked) {
            this.checked = checked;
            return this;
        }

        /**
         * Lets the command run only when it can do something, such as Delete Cells with cells selected.
         *
         * @param enabled whether it can run now
         * @return this action
         */
        CommandAction enabledWhen(final BooleanSupplier enabled) {
            this.enabled = enabled;
            return this;
        }

        private void refresh() {
            if (label != null) {
                putValue(NAME, label.get());
            }
            if (checked != null) {
                putValue(SELECTED_KEY, checked.getAsBoolean());
            }
            if (enabled != null) {
                setEnabled(enabled.getAsBoolean());
            }
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            run.run();
            afterRun.run();
        }
    }
}
