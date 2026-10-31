import type {HelpDocSectionInput} from "../helpDocTypes";
import {startHereSection} from "./startHereSection";
import {identitySection} from "./identitySection";
import {exchangesSection} from "./exchangesSection";
import {adminOperationsSection} from "./adminOperationsSection";
import {workflowsSection} from "./workflowsSection";
import {blueprintsSection} from "./blueprintsSection";
import {variablesSection} from "./variablesSection";
import {fieldsSection} from "./fieldsSection";
import {informationRequestsSection} from "./informationRequestsSection";
import {communicationsSection} from "./communicationsSection";
import {documentLibrarySection} from "./documentLibrarySection";

export const helpDocSections: HelpDocSectionInput[] = [
    startHereSection,
    identitySection,
    exchangesSection,
    adminOperationsSection,
    workflowsSection,
    blueprintsSection,
    variablesSection,
    fieldsSection,
    informationRequestsSection,
    communicationsSection,
    documentLibrarySection,
];
