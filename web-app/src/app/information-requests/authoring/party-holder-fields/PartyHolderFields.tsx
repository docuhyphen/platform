import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {AddPartyForm, PartyHolder} from "../add-party-dialog/addPartyForm.ts";
import {PartyGroupCandidate} from "../add-party-dialog/usePartyCandidates.ts";

interface Props
{
    form: AddPartyForm;
    groups: PartyGroupCandidate[];
    disabled: boolean;
    onChange: (change: Partial<AddPartyForm>) => void;
}

const HOLDERS: {value: PartyHolder; label: string}[] = [
    {value: "email", label: "A person by email"},
    {value: "group", label: "A group"},
];

const PartyHolderFields = ({form, groups, disabled, onChange}: Props) => (
    <>
        <ChoiceSelect id={"information-request-add-party-holder"}
                      label={"Holder"}
                      value={form.holder}
                      disabled={disabled}
                      options={HOLDERS}
                      onChange={holder => onChange({holder})}/>
        {form.holder === "email" && (
            <TextField id={"information-request-add-party-email"}
                       label={"Email address"}
                       hint={"Someone without an account answers through an access link you send them."}
                       value={form.email}
                       disabled={disabled}
                       maxLength={320}
                       onChange={email => onChange({email})}/>
        )}
        {form.holder === "group" && (
            <ChoiceSelect id={"information-request-add-party-group"}
                          label={"Group"}
                          value={form.groupId}
                          placeholder={"Choose a group"}
                          disabled={disabled}
                          hint={groups.length === 0 ? "No group is available to you." : undefined}
                          options={groups.map(group => ({value: group.id, label: group.name}))}
                          onChange={groupId => onChange({groupId})}/>
        )}
    </>
);

export default PartyHolderFields;
