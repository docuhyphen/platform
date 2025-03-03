import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {DocumentBasicDto, SharingSessionBasicDto, SharingSessionStatus} from "../models/models.tsx";
import NoAuthSessionHeader from "./components/header/NoAuthSessionHeader.tsx";
import {useNoAuthSharingSessionStyles} from "./NoAuthSharingSessionStyles.tsx";
import NoAuthSessionUserDecision from "./components/session-use-decision/NoAuthSessionUserDecision.tsx";
import NoAuthSessionDocumentList from "./components/document-list/NoAuthSessionDocumentList.tsx";
import {Spinner} from "@fluentui/react-components";
import {fetchNoAuthSharingSession} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";

const NoAuthSharingSession: React.FC = () =>
{
    const styles = useNoAuthSharingSessionStyles();
    const navigate = useNavigate();
    const token = useToken();

    const [isLoadingSession, setIsLoadingSession] = useState(false);
    const [sessionId, setSessionId] = useState('');
    const [sessionAccepted, setSessionAccepted] = useState(false);
    const [session, setSession] = useState<SharingSessionBasicDto>();
    const [sessionDocuments, setSessionDocuments] = useState<DocumentBasicDto[]>([]);

    useEffect(() =>
    {
        const queryParams = new URLSearchParams(window.location.search);
        if (!queryParams.has('s') || !queryParams.get('s') || !queryParams.get('s')?.length)
        {
            navigate('/sign-in');
        }

        setSessionId(queryParams.get('s'));

    }, [navigate]);

    const fetchSession = async () =>
    {
        if (isLoadingSession)
        {
            return;
        }

        setIsLoadingSession(true);

        try
        {
            const session = (await fetchNoAuthSharingSession(sessionId, token)) as SharingSessionBasicDto;

            if (session.status === SharingSessionStatus.INITIATED)
            {
                setSessionAccepted(false);
            }
            else if (session.status === SharingSessionStatus.ACCEPTED_STARTED)
            {
                setSessionAccepted(true);
            }
            else
            {
                navigate('/sign-in');
            }

            setSession(session);

        }
        catch (error)
        {
            console.error(error);
        }
        finally
        {
            setIsLoadingSession(false);
        }
    }

    useEffect(() =>
    {
        setIsLoadingSession(true);

        setTimeout(() =>
        {
            setIsLoadingSession(false);
        }, 4000);

    }, []);

    return (
        <section className={styles.container}>
            <NoAuthSessionHeader/>

            {isLoadingSession &&
                <div className={styles.sessionLoadingContainer}>
                    <Spinner size={"small"} label={"Loading..."}/>
                </div>
            }

            {!isLoadingSession && <>

                {sessionAccepted &&
                    <>
                        <NoAuthSessionDocumentList/>
                    </>
                }
                    {!sessionAccepted &&
                        <NoAuthSessionUserDecision
                            sessionId={session}
                            onAccepted={() => setSessionAccepted(true)}
                            onDeclined={() => navigate("/sign-in/")}/>
                    }
            </>
            }
        </section>
    );
}

export default NoAuthSharingSession;