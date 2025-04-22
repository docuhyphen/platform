import {Button, Divider, Text} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserRole} from "../../models/models.tsx";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";
import OrganizationOnboardingDialog from "./organization-onboarding-dialog/OrganizationOnboardingDialog.tsx";
import {useState} from "react";
import {PairOrgTabIcon} from "../../components/IconBundles.tsx";

const OrganizationTab = () =>
{
    const styles = useOrganizationTabStyles()
    const {appUser, appUserPersonOrganization} = useAuth()
    const [isOnboardingDialogOpen, setOnboardingDialogOpen] = useState(false)

    const appUserCanManageOrganization = () =>
    {
        return appUserPersonOrganization && appUser?.role === AppUserRole.ORG_ADMIN
    }

    return <>
        {!appUserPersonOrganization && <section className={styles.orgOnboardingContainer}>
            <Text>
                You are not part of an organization. You can onboard your organization to use the full
                potential of Doc-Hyphen.
            </Text>
            <div>
                <Button shape={"circular"}
                        appearance={"outline"}
                        onClick={() => setOnboardingDialogOpen(true)}
                        icon={<></>}>
                    Onboard your organization
                </Button>
            </div>
            <OrganizationOnboardingDialog isOpen={isOnboardingDialogOpen}
                                          onTriggerRegister={() => setOnboardingDialogOpen(false)}
                                          onDismiss={() => setOnboardingDialogOpen(false)}/>
        </section>
        }

        {!appUserCanManageOrganization && <>
            You are not allowed to manage your organization.
        </>
        }

        {appUserCanManageOrganization() &&

            <div>
                <p>
                    Allow other users outside your organization to search for you
                </p>
                <Divider/>
                <Text>
                    Pared Organizations
                </Text>
                <Button icon={<PairOrgTabIcon/>}
                        shape={"circular"}>
                    Find and Pair
                </Button>
            </div>
        }
    </>
}

export default OrganizationTab;