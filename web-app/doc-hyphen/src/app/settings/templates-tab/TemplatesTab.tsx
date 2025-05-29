import {Button} from "@fluentui/react-components";
import {TemplateAddIcon} from "../../components/IconBundles.tsx";
import {useTemplatesTabStyles} from "./TemplatesTabStyles.tsx";

const TemplatesTab = () =>
{
    const styles = useTemplatesTabStyles();

    return <>
        <div className={styles.tabContainer}>
            <div className={styles.header}>
                <div></div>
                <Button icon={<TemplateAddIcon/>}
                        appearance="primary"
                        shape={"circular"}>
                    Create new template
                </Button>
            </div>
        </div>
    </>
}

export default TemplatesTab;