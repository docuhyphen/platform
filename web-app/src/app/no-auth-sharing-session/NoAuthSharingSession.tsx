import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {NoAuthSharingSessionBasicDto, SharingSessionStatus} from "../models/models.tsx";
import NoAuthSessionHeader from "./components/header/NoAuthSessionHeader.tsx";
import {useNoAuthSharingSessionStyles} from "./NoAuthSharingSessionStyles.tsx";
import NoAuthSessionUserDecision from "./components/session-use-decision/NoAuthSessionUserDecision.tsx";
import NoAuthSessionDocumentList from "./components/document-list/NoAuthSessionDocumentList.tsx";
import {Spinner, Text} from "@fluentui/react-components";
import {fetchNoAuthSharingSession} from "../../services/sharingSessionApi.ts";

const NoAuthSharingSession: React.FC = () =>
{
    const styles = useNoAuthSharingSessionStyles();
    const navigate = useNavigate();

    const [isLoadingSession, setIsLoadingSession] = useState(false);
    const [sessionId, setSessionId] = useState<string | null>(null);
    const [sessionAccepted, setSessionAccepted] = useState(false);
    const [session, setSession] = useState<NoAuthSharingSessionBasicDto>(null);

    useEffect(() =>
    {
        const queryParams = new URLSearchParams(window.location.search);
        const sessionIdParam = queryParams.get('s');

        if (!sessionIdParam)
        {
            navigate('/sign-in');
        }
        else
        {
            setSessionId(sessionIdParam);
        }
    }, [navigate]);

    const fetchSession = async () =>
    {
        if (!sessionId)
        {
            return;
        }

        if (isLoadingSession)
        {
            return;
        }

        setIsLoadingSession(true);

        try
        {
            const session = await fetchNoAuthSharingSession(sessionId) as NoAuthSharingSessionBasicDto;

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
            navigate('/sign-in');
        }
        finally
        {
            setIsLoadingSession(false);
        }
    };

    useEffect(() =>
    {
        if (sessionId)
        {
            fetchSession();
        }
    }, [sessionId]);

    const onSessionAccepted = (session: NoAuthSharingSessionBasicDto) =>
    {
        setSession(session);
        setSessionAccepted(true)
    }

    return (
        <section className={styles.container}>
            <NoAuthSessionHeader/>

            {isLoadingSession && (
                <div className={styles.sessionLoadingContainer}>
                    <Spinner size={"small"} label={"Loading..."}/>
                </div>
            )}

            {!isLoadingSession && session && (
                <>
                    {sessionAccepted ? (
                        <section className={styles.sessionContainer}>
                            <div className={styles.sessionName}>
                                {(session.initiatorLastName && session.initiatorFirstName) &&
                                    <Text>
                                        Requested by {session.initiatorFirstName} {session.initiatorLastName}
                                    </Text>
                                }
                                <Text size={600}>{session.sessionName}</Text>
                            </div>
                            <NoAuthSessionDocumentList
                                session={session}/>
                        </section>
                    ) : (
                        <section className={styles.sessionDecisionContainer}>
                            <NoAuthSessionUserDecision
                                session={session}
                                onAccepted={onSessionAccepted}
                                onDeclined={() => navigate("/sign-in/")}
                            />
                        </section>
                    )}
                </>
            )}
        </section>
    );
};

export default NoAuthSharingSession;