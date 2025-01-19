import React from 'react';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import Home from './app/home';
import SignIn from './app/sign-in';
import SignUp from './app/sign-up';
import IndividualRegistration from './app/individual-registration';
import CompanyRegistration from './app/company-registration';
import './App.css';

const App: React.FC = () => {
    return (
        <Router>
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/sign-in" element={<SignIn />} />
                <Route path="/sign-up" element={<SignUp />} />
                <Route path="/individual-registration" element={<IndividualRegistration />} />
                <Route path="/company-registration" element={<CompanyRegistration />} />
            </Routes>
        </Router>
    );
};

export default App;