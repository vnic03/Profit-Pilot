# Profit Pilot Frontend

React and TypeScript frontend, built with Vite and styled with CSS/SCSS. Charts use Recharts; tests use Vitest and React Testing Library.

For installation, local startup, backend configuration, standard tests, builds, and deployment, see the [development guide](../doc/development.md).

## Source layout

| Location                             | Purpose                                              |
| ------------------------------------ | ---------------------------------------------------- |
| `src/App.tsx`                        | Main application component                           |
| `src/components/`                    | Dashboard sections and reusable UI components        |
| `src/components/stock-analysis/`     | Stock selection, price loading, and analysis view    |
| `src/components/charts/stock-chart/` | Stock charts, series controls, and history selection |
| `src/service/`                       | Backend requests, data loading, and shared hooks     |
| `src/service/apiConfig.js`           | Shared API base URL configuration                    |
| `src/setupTests.ts`                  | Shared test setup                                    |

Component and hook tests live alongside their source files as `*.test.tsx` or `*.test.ts`.

## Development utilities

Run these commands from this directory:

| Command              | Purpose                                      |
| -------------------- | -------------------------------------------- |
| `npm run typecheck`  | Check TypeScript without building            |
| `npm run test:watch` | Rerun tests while editing                    |
| `npm run preview`    | Preview an existing production build locally |

The preview server is for local inspection; it does not start the backend.

## Making UI changes

- Keep backend request logic in `src/service/` and reuse the shared API configuration.
- Show loading, empty, and error states when displaying asynchronous data.
- Join price and indicator series by date; indicators may omit initial dates while accumulating enough history.
- Keep frontend tests close to the affected components or hooks.
- Keep provider credentials on the backend. Values exposed through Vite's `VITE_*` variables are visible to the browser.
