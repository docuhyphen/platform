import {useState} from "react";
import {
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Persona,
    Spinner,
} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {InfoIcon, SettingsIcon, SignOutButtonIcon} from "../../IconBundles.tsx";
import SignOutClickSurface from "../../SignOutClickSurface.tsx";
import {useAccountMenuStyles} from "./AccountMenuStyles.tsx";

const MAX_DISPLAY_EMAIL_LENGTH = 36;

const formatEmailForDisplay = (email?: string): string | undefined =>
{
    if (!email || email.length <= MAX_DISPLAY_EMAIL_LENGTH)
    {
        return email;
    }

    const atIndex = email.indexOf("@");
    if (atIndex === -1)
    {
        return `${email.slice(0, MAX_DISPLAY_EMAIL_LENGTH - 3)}...`;
    }

    const domainPart = email.slice(atIndex + 1);
    const allowedLocalLength = MAX_DISPLAY_EMAIL_LENGTH - domainPart.length - 4;
    return allowedLocalLength < 6
        ? `${email.slice(0, MAX_DISPLAY_EMAIL_LENGTH - 3)}...`
        : `${email.slice(0, allowedLocalLength)}...@${domainPart}`;
};

interface AccountMenuProps
{
    onToggleHelpSidebar: () => void;
}

const AccountMenu = ({onToggleHelpSidebar}: AccountMenuProps) =>
{
    const {appUser} = useAuth();
    const navigate = useNavigate();
    const styles = useAccountMenuStyles();
    const [isSignOutDialogOpen, setIsSignOutDialogOpen] = useState(false);

    return (
        <div
            id={"tour-account-btn"}
            className={styles.tourAnchor}>
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <Button
                        id={"account-menu-btn"}
                        appearance={"subtle"}
                        shape={"circular"}
                        className={styles.persona}
                        aria-label={appUser?.email ? `Account menu for ${appUser.email}` : "Account menu"}
                        title={appUser?.email}>
                        <Persona
                            id={"account-menu-persona"}
                            name={`${appUser?.person?.firstName} ${appUser?.person?.lastName}`}
                            secondaryText={formatEmailForDisplay(appUser?.email)}/>
                    </Button>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList id={"account-menu-list"}>
                        <MenuItem
                            id={"account-menu-settings"}
                            onClick={() => navigate("/settings")}
                            icon={<SettingsIcon/>}>
                            Settings
                        </MenuItem>
                        <MenuItem
                            id={"account-menu-help"}
                            onClick={onToggleHelpSidebar}
                            icon={<InfoIcon/>}>
                            Help
                        </MenuItem>
                        <MenuItem
                            id={"account-menu-sign-out"}
                            icon={<SignOutButtonIcon/>}>
                            <SignOutClickSurface onSignOut={() => setIsSignOutDialogOpen(true)}/>
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>

            <Dialog open={isSignOutDialogOpen}>
                <DialogSurface id={"sign-out-dialog-surface"}>
                    <DialogBody id={"sign-out-dialog-body"}>
                        <DialogTitle id={"sign-out-dialog-title"}/>
                        <DialogContent id={"sign-out-dialog-content"}>
                            <Spinner
                                id={"sign-out-dialog-spinner"}
                                label={"Signing out..."}
                                size={"small"}/>
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default AccountMenu;
