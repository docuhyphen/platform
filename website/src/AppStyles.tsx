import {makeStyles} from "@fluentui/react-components";

export const appStyles = makeStyles({
    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        width: "100%",
        overflow: "hidden",
        backgroundColor: "#f8faff",

        "::before": {
            content: '""',
            position: "absolute",
            inset: "-25%",
            background: `
                radial-gradient(circle at -1% 20%, rgb(94, 161, 231) 0%, transparent 40%),
                radial-gradient(circle at 70% 40%, rgb(84, 130, 193) 0%, transparent 16%),
                radial-gradient(circle at 50% 80%, rgb(75, 100, 150) 0%, transparent 17%)
            `,
            filter: "blur(8.75rem) saturate(110%)",
            opacity: 0.35,
            zIndex: 0,
        },

        "> *": {
            position: "relative",
            zIndex: 1,
        },
    },
});

