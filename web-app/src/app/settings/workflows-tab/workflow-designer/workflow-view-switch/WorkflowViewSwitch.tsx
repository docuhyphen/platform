import {
    Menu,
    MenuButton,
    MenuItemRadio,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip,
} from "@fluentui/react-components";
import {DiagramIcon, EditIcon, SplitViewIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowViewSwitchStyles} from "./WorkflowViewSwitchStyles.tsx";

export type WorkflowDesignerView = "form" | "diagram" | "both";

interface Props
{
    view: WorkflowDesignerView;
    onChange: (view: WorkflowDesignerView) => void;
}

const WorkflowViewSwitch = ({view, onChange}: Props) =>
{
    const styles = useWorkflowViewSwitchStyles();
    const Icon = {
        form: EditIcon,
        diagram: DiagramIcon,
        both: SplitViewIcon,
    }[view];

    return (
        <div className={styles.switchBar}>
            <Menu
                checkedValues={{view: [view]}}
                onCheckedValueChange={(_, data) =>
                {
                    const selection = data.checkedItems[0] as WorkflowDesignerView | undefined;
                    if (selection) onChange(selection);
                }}
            >
                <MenuTrigger disableButtonEnhancement>
                    <Tooltip
                        content={`View: ${view === "form" ? "Form" : view === "diagram" ? "Diagram" : "Both"}`}
                        relationship="label"
                    >
                        <MenuButton
                            id="workflow-designer-view-toggle-button"
                            className={styles.toggle}
                            appearance="subtle"
                            shape="circular"
                            menuIcon={null}
                            aria-label={`View: ${view === "form" ? "Form" : view === "diagram" ? "Diagram" : "Both"}`}
                            icon={<Icon className={styles.icon} />}
                        />
                    </Tooltip>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id="workflow-designer-view-switch">
                        <MenuItemRadio
                            id="workflow-designer-view-form"
                            name="view"
                            value="form"
                            icon={<EditIcon />}
                        >
                            Form
                        </MenuItemRadio>
                        <MenuItemRadio
                            id="workflow-designer-view-diagram"
                            name="view"
                            value="diagram"
                            icon={<DiagramIcon />}
                        >
                            Diagram
                        </MenuItemRadio>
                        <MenuItemRadio
                            id="workflow-designer-view-both"
                            name="view"
                            value="both"
                            icon={<SplitViewIcon />}
                        >
                            Both
                        </MenuItemRadio>
                    </MenuList>
                </MenuPopover>
            </Menu>
        </div>
    );
};

export default WorkflowViewSwitch;
