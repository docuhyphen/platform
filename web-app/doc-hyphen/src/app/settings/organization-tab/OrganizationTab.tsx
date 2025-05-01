import {Button, Divider, InfoLabel, Switch, Text} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserRole} from "../../models/models.tsx";
import {useOrganizationTabStyles} from "./OrganizationTabStyles.tsx";
import OrganizationOnboardingDialog from "./organization-onboarding-dialog/OrganizationOnboardingDialog.tsx";
import {useState} from "react";
import {PairOrgTabIcon, ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";

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
                    Register your organization
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

            <div className={styles.container}>
                <Switch
                    label={
                        <InfoLabel
                            info={
                                <>
                                    This setting allows other users to find and initiate sharing sessions with your
                                    organization without pairing first. This makes your organization more discoverable.
                                </>
                            }>
                            Allow Initiate Sharing Sessions without pairing
                        </InfoLabel>
                    }
                />


                <Divider alignContent={"start"}
                         appearance={"brand"}>
                    Basic Details
                    <Button icon={<ProfileEditBasicDetailsIcon/>}
                            appearance={"subtle"}/>
                </Divider>
                <div className={styles.dataContainer}>
                    <Text size={500}
                          italic={true}
                          className={styles.dataName}>
                        Name
                    </Text>
                    <Text size={500}>
                        {appUserPersonOrganization?.name}
                    </Text>
                </div>
                <div className={styles.dataContainer}>
                    <Text size={500}
                          italic={true}
                          className={styles.dataName}>
                        Registration Number
                    </Text>
                    <Text size={500}>
                        {appUserPersonOrganization?.registrationNumber}
                    </Text>
                </div>

                <Divider alignContent={"start"}
                         appearance={"brand"}>
                    Contact Details
                    <Button icon={<ProfileEditBasicDetailsIcon/>}
                            appearance={"subtle"}/>
                </Divider>

                <div className={styles.dataContainer}>
                    <Text size={500}
                          italic={true}
                          className={styles.dataName}>
                        Email
                    </Text>
                    <Text size={500}>
                        {appUserPersonOrganization?.contactDetails?.email}
                    </Text>
                </div>
                <div className={styles.dataContainer}>
                    <Text size={500}
                          italic={true}
                          className={styles.dataName}>
                        Phone number
                    </Text>
                    <Text size={500}>
                        <Button appearance={"outline"}
                                shape={"circular"}
                                size={"small"}>
                            Add Phone Number
                        </Button>
                    </Text>
                </div>
                <Divider appearance={"brand"}
                         alignContent={"start"}>
                    Pared Organizations
                </Divider>
                <div>
                    <Button icon={<PairOrgTabIcon/>}
                            shape={"circular"}>
                        Find and Pair
                    </Button>
                </div>
            </div>
        }
    </>
}

export default OrganizationTab;