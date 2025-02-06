import React, {useEffect, useState} from 'react';
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {fetchSignedInUserAppUserSharingSession} from "../../services/api.ts";
import useToken from "../../context/useToken.tsx";
import "./Landing.css";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {Accordion} from "@fluentui/react-components";

const Landing: React.FC = () =>
{
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [sessionDetails, setSessionDetails] = useState<any>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();

    useEffect(() =>
    {
        const randomDelay = Math.floor(Math.random() * 10000) + 5000;
        setTimeout(() =>
        {
            setIsLoading(false);
        }, randomDelay);
    }, []);

    useEffect(() =>
    {
        if (selectedSessionId)
        {
            const fetchDetails = async () =>
            {
                try
                {
                    const details = await fetchSignedInUserAppUserSharingSession(selectedSessionId, token);
                    setSessionDetails(details);
                }
                catch (error)
                {
                    console.error(error);
                }
            };
            fetchDetails();
        }
    }, [selectedSessionId]);

    return (
        isLoading ? <PreLanding/> :
            <section id={"sharing-sessions-container"}>
                <div>
                    <SharingSessionList onSelectionChange={setSelectedSessionId}/>
                </div>
                <div>
                    <h1>Sharing Session Details</h1>
                    {sessionDetails && (
                        <div>
                            <p>Session Name: {sessionDetails.sessionName}</p>
                            <p>Description: {sessionDetails.description}</p>
                            <p>Created Date: {sessionDetails.createdDate}</p>
                            {
                                sessionDetails.documents?.map((document: any) =>{

                                    <div>
                                        {document.title}
                                    </div>
                                })
                            }
                        </div>
                    )}
                </div>
            </section>
    );
};

export default Landing;