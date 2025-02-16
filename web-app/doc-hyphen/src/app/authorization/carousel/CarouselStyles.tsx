import {makeStyles} from "@fluentui/react-components";

export const useCarouselStyles = makeStyles({
    carousel: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        overflow: "hidden",
    },
    carouselInner: {
        display: "flex",
        transition: "transform 0.5s ease-in-out",
        maxWidth: "100%",
    },
    carouselItem: {
        minWidth: "100%",
        boxSizing: "border-box",
        padding: "20px",
        textAlign: "center",
    },
    carouselDots: {
        display: "flex",
        gap: "5px",
        marginTop: "10px",
    },
    dot: {
        width: "10px",
        height: "10px",
        backgroundColor: "#ccc",
        borderRadius: "50%",
        cursor: "pointer",
    },
    dotActive: {
        backgroundColor: "#333",
    },
});