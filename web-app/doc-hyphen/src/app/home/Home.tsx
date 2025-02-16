import React from 'react';
import {useNavigate} from 'react-router-dom';
import {Button} from "@fluentui/react-components";
import {useHomeStyles} from './HomeStyles';

const Home: React.FC = () => {
    const navigate = useNavigate();
    const styles = useHomeStyles();

    const onSignIn = () => {
        navigate('/sign-in');
    };

    const signUp = () => {
        navigate('/sign-up');
    };

    return (
        <div className={styles.container}>
            <h1 className={styles.heading}>Welcome to doc Hyphen</h1>
            <Button className={styles.button} onClick={onSignIn}> Sign In </Button>
            <Button className={styles.button} onClick={signUp}> Sign Up </Button>
        </div>
    );
};

export default Home;