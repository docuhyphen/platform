import type {HelpDocSectionInput} from "../helpDocTypes";
import {startHereSection}        from "./startHereSection";
import {identitySection}         from "./identitySection";
import {exchangesSection}        from "./exchangesSection";
import {adminOperationsSection}  from "./adminOperationsSection";
import {workflowsSection}        from "./workflowsSection";
import {blueprintsSection}       from "./blueprintsSection";
import {variablesSection}        from "./variablesSection";
import {fieldsSection}           from "./fieldsSection";
import {communicationsSection}   from "./communicationsSection";
import {documentLibrarySection}  from "./documentLibrarySection";

/**
 * Every help section, in the order the sidebar lists them. This is the only place a section is
 * named, so adding one is an import and a list entry here and nothing else.
 */
export const helpDocSections: HelpDocSectionInput[] = [
    startHereSection,
    identitySection,
    exchangesSection,
    adminOperationsSection,
    workflowsSection,
    blueprintsSection,
    variablesSection,
    fieldsSection,
    communicationsSection,
    documentLibrarySection,
];
