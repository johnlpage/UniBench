import {MongoClient} from 'mongodb';
import {promises as fs} from 'fs';
import {ChartJSNodeCanvas} from 'chartjs-node-canvas';

class MarkdownReportGenerator {


    constructor(mongoUrl, dbName) {
        this.debugMode = false;
        this.client = new MongoClient(mongoUrl);
        this.dbName = dbName;
        this.chartRenderer = new ChartJSNodeCanvas({width: 800, height: 400});
    }

    async generateReport(templatePath, outputPath) {
        await this.client.connect();

        // Read template  
        let markdown = await fs.readFile(templatePath, 'utf8');

        // Replace
        // data pla
        // ceholders
        markdown = await this.replaceTablePlaceholders(markdown);
        markdown = await this.replaceChartPlaceholders(markdown);

        // Write output  
        await fs.writeFile(outputPath, markdown);
        await this.client.close();
    }

    async replaceTablePlaceholders(markdown) {
        const tableRegex = /<!--\s*MONGO_TABLE:\s*(.+?)\s*-->/gs;
        let match;
        let updatedMarkdown = markdown; // Create a separate copy

        while ((match = tableRegex.exec(markdown)) !== null) {
            const query = JSON.parse(match[1]);
            const table = await this.generateTable(query);
            updatedMarkdown = updatedMarkdown.replace(match[0], table); // Work on updatedMarkdown
        }

        return updatedMarkdown; // Return the updated string
    }

    async replaceChartPlaceholders(markdown) {
        const chartRegex = /<!--\s*MONGO_CHART:\s*(.+?)\s*-->/g;
        let match;

        while ((match = chartRegex.exec(markdown)) !== null) {
            const chartConfig = JSON.parse(match[1]);
            const chart = await this.generateChart(chartConfig);
            markdown = markdown.replace(match[0], chart);
        }

        return markdown;
    }

    async generateTable({collection, pipeline, headers, columns}) {
        const db = this.client.db(this.dbName);
        const results = await db.collection(collection).aggregate(pipeline).toArray();

        // Generate markdown table  
        let table = `| ${headers.join(' | ')} |\n`;
        table += `| ${headers.map(() => '--:').join(' | ')} |\n`;

        results.forEach(row => {
            const values = columns.map(column => {
                const value = row[column];
                return value === null || value === undefined ? '' : value;
            });
            table += `| ${values.join(' | ')} |\n`;
        });

        if (this.debugMode) {
            table += "\n```\n" + JSON.stringify(results, null, 2) + "\n```\n"
        }
        return table
    }

    async generateChart({collection, pipeline, chartType, title}) {
        const db = this.client.db(this.dbName);
        const data = await db.collection(collection).aggregate(pipeline).toArray();

        const chartConfig = {
            type: chartType,
            data: {
                labels: data.map(item => item.label),
                datasets: [{
                    label: title,
                    data: data.map(item => item.value),
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    borderColor: 'rgba(54, 162, 235, 1)',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: false,
                plugins: {
                    title: {
                        display: true,
                        text: title
                    }
                }
            }
        };

        const imageBuffer = await this.chartRenderer.renderToBuffer(chartConfig);
        const base64Image = imageBuffer.toString('base64');

        return `![${title}](data:image/png;base64,${base64Image})`;
    }
}

const mongoUri = process.env.MONGO_REPORTING_URI || 'mongodb://localhost:27017';
const dbName = process.env.REPORTING_DB_NAME || 'unibench';

const args = process.argv.slice(2);

if (args.length < 2) {
    console.error("Usage: node MarkdownReportGenerator.js <template.md> [output.md]")
    process.exit(1)
}
const template = args[0]
const output = args[1] || args[0].replace('.md', '-output.md')


const generator = new MarkdownReportGenerator(mongoUri, dbName);
await generator.generateReport(template, output);  
