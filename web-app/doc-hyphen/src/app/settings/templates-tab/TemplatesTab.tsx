import {Button} from "@fluentui/react-components";
import {TemplateAddIcon} from "../../components/IconBundles.tsx";

const TemplatesTab = () =>
{

    return <>
        <div>
            <Button icon={<TemplateAddIcon/>}
                    shape={"circular"}>
                Create new template
            </Button>
        </div>
    </>
}

export default TemplatesTab;