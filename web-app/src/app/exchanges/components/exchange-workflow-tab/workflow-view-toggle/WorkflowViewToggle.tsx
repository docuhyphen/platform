import {
    Menu,
    MenuButton,
    MenuItemRadio,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip,
} from "@fluentui/react-components";
import {DiagramIcon, SplitViewIcon, TimelineIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowViewToggleStyles} from "./WorkflowViewToggleStyles.tsx";

export type WorkflowViewMode = "TIMELINE" | "DIAGRAM" | "BOTH";

interface Props
{
    mode: WorkflowViewMode;
    onChange: (mode: WorkflowViewMode) => void;
}

const MODE_ICONS: Record<WorkflowViewMode, typeof TimelineIcon> = {
    TIMELINE: TimelineIcon,
    DIAGRAM: DiagramIcon,
    BOTH: SplitViewIcon,
};

const MODE_LABELS: Record<WorkflowViewMode, string> = {
    TIMELINE: "Timeline",
    DIAGRAM: "Diagram",
    BOTH: "Both",
};

const WorkflowViewToggle = ({mode, onChange}: Props) =>
{
    const styles = useWorkflowViewToggleStyles();
    const Icon = MODE_ICONS[mode];

    return (
        <Menu checkedValues={{view: [mode]}}
              onCheckedValueChange={(_, data) =>
              {
                  const selection = data.checkedItems[0] as WorkflowViewMode | undefined;
                  if (selection) onChange(selection);
              }}>
            <MenuTrigger disableButtonEnhancement>
                <Tooltip content={`View: ${MODE_LABELS[mode]}`}
                         relationship="label">
                    <MenuButton id="exchange-workflow-view-toggle-button"
                                className={styles.toggle}
                                appearance="subtle"
                                shape="circular"
                                menuIcon={null}
                                aria-label={`View: ${MODE_LABELS[mode]}`}
                                icon={<Icon className={styles.icon} />} />
                </Tooltip>
            </MenuTrigger>
            <MenuPopover id={"workflow-view-toggle-menu-popover-1"}>
                <MenuList id="exchange-workflow-view-menu">
                    <MenuItemRadio id="exchange-workflow-view-timeline"
                                   name="view"
                                   value="TIMELINE"
                                   icon={<TimelineIcon />}>
                        Timeline
                    </MenuItemRadio>
                    <MenuItemRadio id="exchange-workflow-view-diagram"
                                   name="view"
                                   value="DIAGRAM"
                                   icon={<DiagramIcon />}>
                        Diagram
                    </MenuItemRadio>
                    <MenuItemRadio id="exchange-workflow-view-both"
                                   name="view"
                                   value="BOTH"
                                   icon={<SplitViewIcon />}>
                        Both
                    </MenuItemRadio>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default WorkflowViewToggle;
