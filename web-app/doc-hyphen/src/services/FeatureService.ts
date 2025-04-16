import {AppUser} from "../app/models/models.tsx";

enum Feature {
    EDIT_SHARING_SESSION = "EDIT_SHARING_SESSION",
}

const canEditSharingSession = (appUser: AppUser): boolean => {


}

export const canUseFeature = (feature: string, appUser: AppUser): boolean => {

    switch (feature)
    {
        case Feature.EDIT_SHARING_SESSION:
            return canEditSharingSession(appUser);

        default:
            return false;
    }
}