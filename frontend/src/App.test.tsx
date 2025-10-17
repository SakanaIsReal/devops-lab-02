import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import React from 'react';

const renderWithRouter = (ui: React.ReactElement) => {
  return render(
    <BrowserRouter>
      {ui}
    </BrowserRouter>
  );
};

describe('App Component', () => {
  it('renders without crashing', () => {
    const { container } = renderWithRouter(<App />);
    expect(container).toBeInTheDocument();
  });
});