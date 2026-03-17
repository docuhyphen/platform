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
});

export function LandingHeader()
{
	const styles = useStyles();

	return (
		<header className={styles.wrapper}>
			<nav className={styles.nav} aria-label="Primary">
				<AppLogo/>
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
			</nav>
		</header>
	);
}

