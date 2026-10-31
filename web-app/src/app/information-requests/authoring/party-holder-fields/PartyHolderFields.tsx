import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {
    PartyCandidate,
    PartyHolder,
    partyHolderLabels,
    PartyHolderSelection,
    TrustedPartyCandidate,
} from "./partyHolder.ts";

interface Props
{
    idPrefix: string;
    holders: PartyHolder[];
    selection: PartyHolderSelection;
    groups: PartyCandidate[];
    people: PartyCandidate[];
    trusted: TrustedPartyCandidate[];
    disabled: boolean;
    onChange: (change: Partial<PartyHolderSelection>) => void;
}

const optionsOf = (candidates: PartyCandidate[]) => candidates.map(candidate => ({
    value: candidate.id,
    label: candidate.name
}));

const trustedHint = (trusted: TrustedPartyCandidate[]): string | undefined =>
    trusted.some(candidate => candidate.trustSuspended)
        ? "Recipients whose organization's trust is suspended cannot be chosen."
        : undefined;

const PartyHolderFields = ({idPrefix, holders, selection, groups, people, trusted, disabled, onChange}: Props) => (
    <>
        <ChoiceSelect id={`${idPrefix}-holder`}
                      label={"Holder"}
                      value={selection.holder}
                      disabled={disabled}
                      options={holders.map(holder => ({value: holder, label: partyHolderLabels[holder]}))}
                      onChange={holder => onChange({holder})}/>
        {selection.holder === "email" && (
            <TextField id={`${idPrefix}-email`}
                       label={"Email address"}
                       hint={"Use the email address of the person who will answer this request."}
                       value={selection.email}
                       disabled={disabled}
                       maxLength={320}
                       onChange={email => onChange({email})}/>
        )}
        {selection.holder === "user" && (
            <ChoiceSelect id={`${idPrefix}-person`}
                          label={"Person"}
                          value={selection.userId}
                          placeholder={"Choose a person"}
                          disabled={disabled}
                          hint={people.length === 0 ? "No one with an account is available to you." : undefined}
                          options={optionsOf(people)}
                          onChange={userId => onChange({userId})}/>
        )}
        {selection.holder === "trusted" && (
            <ChoiceSelect id={`${idPrefix}-trusted`}
                          label={"Trusted recipient"}
                          value={selection.trustedRecipientId}
                          placeholder={"Choose a trusted recipient"}
                          disabled={disabled}
                          hint={trustedHint(trusted)}
                          options={trusted
                              .filter(candidate => !candidate.trustSuspended)
                              .map(candidate => ({value: candidate.exchangeRecipientId, label: candidate.name}))}
                          onChange={trustedRecipientId => onChange({trustedRecipientId})}/>
        )}
        {selection.holder === "group" && (
            <ChoiceSelect id={`${idPrefix}-group`}
                          label={"Group"}
                          value={selection.groupId}
                          placeholder={"Choose a group"}
                          disabled={disabled}
                          hint={groups.length === 0 ? "No group is available to you." : undefined}
                          options={optionsOf(groups)}
                          onChange={groupId => onChange({groupId})}/>
        )}
    </>
);

export default PartyHolderFields;
