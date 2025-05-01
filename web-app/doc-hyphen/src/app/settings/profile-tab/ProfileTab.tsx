import {Button, Divider, Switch, Text} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";

const ProfileTab = () =>
{
    const {appUser} = useAuth()
    const styles = useProfileTabStyles()

    return <>
        <div className={styles.container}>
            <Switch
                label="Send me by email every time I sign in"
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
                    First Name
                </Text>
                <Text size={500}>
                    {appUser?.person?.firstName}
                </Text>
            </div>
            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Last Name
                </Text>
                <Text size={500}>
                    {appUser?.person?.lastName}
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
                    {appUser?.email}
                </Text>
            </div>
            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Phone number
                </Text>
                <Text size={500}>
                    {appUser?.person?.contactDetails?.phoneNumber ? (
                        appUser.person.contactDetails.phoneNumber
                    ) : (
                        <Button appearance={"outline"}
                                shape={"circular"}
                                size={"small"}>
                            Add Phone Number
                        </Button>
                    )}
                </Text>
            </div>
            <Divider alignContent={"start"}
                     appearance={"brand"}>
                Security
            </Divider>
            <div>
                <Button appearance={"outline"}
                        shape={"circular"}
                        size={"medium"}> Change password</Button>
            </div>
            <div>
                <Button appearance={"outline"}
                        shape={"circular"}
                        size={"medium"}> Sign out of all devices</Button>
            </div>
        </div>
    </>
}

export default ProfileTab;