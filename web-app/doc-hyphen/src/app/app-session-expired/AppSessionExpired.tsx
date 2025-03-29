import React, {useEffect} from "react";
import {useAuth} from "../../context/AuthContext.tsx";
import {isTokenExpired} from "../../utils/helpers.ts";
import {useNavigate} from "react-router-dom";

const AppSessionExpired: React.FC = () =>
{
    const {token} = useAuth();
    const navigate = useNavigate();

    useEffect(() =>
    {
        if(token != null && !isTokenExpired(token))
        {
            alert("IN APP SESSION EXPIRED, token is not null and not expired");
            navigate("/sharing-sessions")
            return;
        }

        alert("IN APP SESSION EXPIRED, token is null or expired");

    });

    return (
        <div>
            <h1>Session Expired</h1>
            <p>Your session has expired. Please sign in again.</p>
        </div>
    );
}

export default AppSessionExpired;