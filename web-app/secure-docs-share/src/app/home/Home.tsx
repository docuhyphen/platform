import React from 'react';
import { PrimaryButton } from '@fluentui/react';
import { useNavigate } from 'react-router-dom';
import './Home.css';

const Home: React.FC = () => {
    const navigate = useNavigate();

    const onSignIn = () => {
        navigate('/sign-in');
    };

    const onJoin = () => {
        navigate('/sign-up');
    };

    return (
        <div>
            <h1>Welcome to Secure Docs Share</h1>
            <PrimaryButton text="Sign In" onClick={onSignIn} />
            <PrimaryButton text="Sign Up" onClick={onJoin} />
        </div>
    );
};

export default Home;