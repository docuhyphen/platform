import {Field, Input} from "@fluentui/react-components";
import {useState} from "react";

const BasicDetailsEditDialog = () =>
{
    const [firstName, setFirstName] = useState('');

    return <>

        <Field label={"First Name"}>
            <Input type="text"/>
        </Field>

        <Field label={"Last Name"}>
            <Input type="text"/>
        </Field>
    </>
}

export default BasicDetailsEditDialog;



