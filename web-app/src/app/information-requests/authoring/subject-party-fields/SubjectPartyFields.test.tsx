// @vitest-environment jsdom
import {useState} from "react";
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, expect, it} from "vitest";
import {InformationRequestSubjectKind} from "../../../models/models.tsx";
import {emptyAddPartyForm} from "../add-party-dialog/addPartyForm.ts";
import SubjectPartyFields from "./SubjectPartyFields.tsx";

afterEach(cleanup);

const SubjectForm = () => {
    const [form, setForm] = useState({...emptyAddPartyForm(), subjectId: "known-record"});
    return <SubjectPartyFields form={form}
                               subjects={[{
                                   id: "known-record",
                                   subjectKind: InformationRequestSubjectKind.PERSON,
                                   references: [],
                                   createdAt: 0
                               }]}
                               referencesAllowed={true}
                               disabled={false}
                               onChange={change => setForm(previous => ({...previous, ...change}))}/>;
};

it("can return from a known subject to a new subject without discarding the form", () => {
    render(<SubjectForm/>);
    fireEvent.change(screen.getByRole("combobox", {name: "Known subject"}), {target: {value: ""}});
    expect(screen.getByRole("combobox", {name: "Subject kind"})).toBeTruthy();
});
