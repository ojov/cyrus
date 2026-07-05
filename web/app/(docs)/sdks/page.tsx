export default function SdksPage() {
  return (
    <div className="doc-prose">
      <h2>
        SDKs <span className="db">Planned</span>
      </h2>
      <p className="lede">
        Official client libraries are on the roadmap. Until then, the REST API is stable and simple to call from any
        language.
      </p>
      <table className="doctable">
        <thead>
          <tr>
            <th>Language</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>Java / Spring</td><td><span className="db db-warn dot">Planned</span></td></tr>
          <tr><td>Node / TypeScript</td><td><span className="db db-warn dot">Planned</span></td></tr>
          <tr><td>Python</td><td><span className="db db-warn dot">Planned</span></td></tr>
          <tr><td>PHP · Go</td><td><span className="db dot">Exploring</span></td></tr>
        </tbody>
      </table>
      <div className="callout">
        Want a specific SDK first? Reach us at <b>hello@trycyrus.app</b> and we will prioritize.
      </div>
    </div>
  );
}
