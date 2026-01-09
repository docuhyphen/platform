import React, {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';

const Home: React.FC = () =>
{

    const navigate = useNavigate();

    useEffect(() =>
    {
        navigate("/sharing-sessions")
    }, [navigate]);

    return (
        <>
        </>
    );
};

export default Home;