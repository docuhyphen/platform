import {createContext, useContext} from "react";
import {RequirementEvidenceCommands} from "./requirementEvidenceCommands.ts";

export interface RequirementEvidenceSettings
{
    uploadAvailable: boolean;
    malwareScanning: boolean;
    commands: RequirementEvidenceCommands;
}

export const RequirementEvidenceContext = createContext<RequirementEvidenceSettings | null>(null);

export const useRequirementEvidenceSettings = (): RequirementEvidenceSettings | null =>
    useContext(RequirementEvidenceContext);
