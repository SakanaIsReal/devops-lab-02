import { render, screen } from '@testing-library/react';
import App from './App';

// Mock react-router-dom
jest.mock('react-router-dom', () => ({
  BrowserRouter: ({ children }) => <div>{children}</div>,
  Routes: ({ children }) => <div>{children}</div>,
  Route: ({ children }) => <div>{children}</div>,
  useNavigate: () => jest.fn(),
}));

test('renders without crashing', () => {
  render(<App />);
  // Basic smoke test
  expect(document.body).toBeInTheDocument();
});
