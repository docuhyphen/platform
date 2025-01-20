import React from 'react';
import { useNavigate } from 'react-router-dom';
import './Home.css';
import {Button} from "@fluentui/react-components";

const Home: React.FC = () => {
    const navigate = useNavigate();

    const onSignIn = () => {
        navigate('/sign-in');
    };

    const signUp = () => {
        navigate('/sign-up');
    };

    return (
        <div>
            <h1>Welcome to Secure Docs Share</h1>
            <Button  onClick={onSignIn}> Sign In </Button>
            <Button onClick={signUp}> Sign Up </Button>
        </div>
    );
};

export default Home;