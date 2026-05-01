import {Button, makeStyles, tokens} from "@fluentui/react-components";
import AppLogo from "../app-logo/AppLogo.tsx";
import {
	BREAKPOINT_MOBILE,
	BUTTON_MIN_WIDTH,
	MENU_PADDING_DESKTOP,
	MENU_PADDING_MOBILE,
	SIGN_IN_URL,
	SPACE_MD,
	WIDTH_CONTENT,
} from "./shared.ts";
import {Link} from "react-router-dom";

const useStyles = makeStyles({
	wrapper: {
		background: tokens.colorNeutralBackground1,
		padding: MENU_PADDING_DESKTOP,
		boxSizing: "border-box",

		[BREAKPOINT_MOBILE]: {
			padding: MENU_PADDING_MOBILE,
		},
	},

	nav: {
		display: "flex",
		flexDirection: "row",
		alignItems: "center",
		width: WIDTH_CONTENT,
		maxWidth: "100%",
		margin: "0 auto",
		justifyContent: "space-between",
		padding: `${SPACE_MD} 0`,
	},

	signInButton: {
		minWidth: BUTTON_MIN_WIDTH,
	},

	linkGroup: {
		display: "flex",
		alignItems: "center",
		gap: SPACE_MD,
	},

	helpLink: {
		color: tokens.colorNeutralForeground2,
		textDecorationLine: "none",
		fontSize: tokens.fontSizeBase300,

		":hover": {
			textDecorationLine: "underline",
		},
	},
});

export function LandingHeader()
{
	const styles = useStyles();

	return (
		<header className={styles.wrapper}>
			<nav className={styles.nav} aria-label="Primary">
				<AppLogo/>
				<div className={styles.linkGroup}>
					<Link to="/help/idp-setup" className={styles.helpLink}>
						IdP Setup Guide
					</Link>
					<Link to="/help/security-faq" className={styles.helpLink}>
						Security FAQ
					</Link>
					<Button
						appearance="outline"
						as="a"
						className={styles.signInButton}
						target="_blank"
						rel="noopener noreferrer"
						shape="circular"
						href={SIGN_IN_URL}
					>
						Sign In
					</Button>
				</div>
			</nav>
		</header>
	);
}
